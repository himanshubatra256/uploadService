package com.monesto.uploadService.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.DirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;
import software.amazon.awssdk.utils.StringUtils;



public class UploadServiceUtil {
	public static final Logger LOGGER = LoggerFactory.getLogger(UploadServiceUtil.class);
	private static final String ALPHA_NUMERIC_SET  = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final String OUTPUT_DIR = "output";
	private static final String BUCKET_NAME = "varcel-upload-service";
	private static final String S3_OUTPUT_DIR = "varcel-output";
	private static final String CREDS_FILE_KEY = "CREDS_VAL";
	private static final String CREDS_FILE_NAME = "credentials.properties";
	private static final String S3_ACCESS_KEY = "s3AccessKey";
	private static final String S3_SECRET_ACCESS_KEY = "s3SecretAccessKey";
	private static final String REDIS_URL = "redis.url";
	private static final String REDIS_PORT = "redis.port";
	private static final String KEY_LENGTH = "keyLen";
	
	private static String awsAccessKey = "";
	private static String secretAccessKey = "";
	private static String redisUrl = "";
	private static Integer redisPort = 0;
	private static Properties properties = new Properties();
	private static Integer keyLen = 6;
	
	
	/**
	 * Function to create randomly generated aplha-numeric Id of the giver length
	 * @param keyLen
	 * @return randomId
	 */
	public static String generateRandomId() {
		LOGGER.info("Entered inside generateRandomId method with key length: " + keyLen);
		Random random = new Random();
		StringBuilder uniqueId = new StringBuilder();
		for(int i = 0;i<keyLen; i++) {
			uniqueId.append(ALPHA_NUMERIC_SET.charAt(random.nextInt(ALPHA_NUMERIC_SET.length())));
		}
		LOGGER.info("Project's unique Id : " + uniqueId.toString());
		return uniqueId.toString();
	}

	
	/**
	 * Clones the project from the given Github's URL into a new folder with name as UniqueId.
	 * @param urlStr
	 * @param uniqueId
	 * @return
	 */
	public static Boolean getProjectFromURL(String urlStr, String uniqueId) {
		LOGGER.info("Entered inside getProjectFromURL.");
		Boolean isDownloaded = Boolean.FALSE;
		try {
			File outputDir = new File(OUTPUT_DIR);

			if(!outputDir.exists()) {
				LOGGER.info("Creating " + OUTPUT_DIR + "folder.");
				outputDir.mkdir();
			}
			File uniqueIdDir = new File(outputDir.getAbsolutePath() + File.separator + uniqueId);
			LOGGER.info("Creating " + uniqueIdDir + "folder.");
			uniqueIdDir.mkdir();

			String finalOutputPath = uniqueIdDir.getAbsolutePath() + File.separator;
			List<String> command = Arrays.asList("git", "clone", urlStr, finalOutputPath);

			// Execute the command
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

			StringBuilder output = new StringBuilder();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append("\n");
			}
			LOGGER.info("Git clone output:\n" + output);

			// Wait for the process to finish
			process.waitFor();

			// Check exit code for success (0)
			int exitCode = process.exitValue();
			if (exitCode != 0) {
				LOGGER.error("Git clone failed with exit code: " + exitCode);
				return isDownloaded;
			}
			LOGGER.info("Git clone successful! Repository downloaded to: " + finalOutputPath);
			int noOfFilesUploaded = uploadToS3(uniqueIdDir.getAbsolutePath(), S3_OUTPUT_DIR + "/" + uniqueId);
			if(noOfFilesUploaded > 0) {
				isDownloaded = Boolean.TRUE;
			}
		}catch(Exception e){
			LOGGER.error("Exception occured in getProjectFromURL: ",e);
		}
		return isDownloaded;
	}
	
	
	/**
	 * Function to upload downloaded objects to S3 bucket.
	 * @param localLocation
	 * @param bucketLocation
	 * @return noOfFilesUploaded
	 */
	public static int uploadToS3(String localLocation, String bucketLocation) {
		LOGGER.info("Entered into uploadToS3 ");
		Instant start = Instant.now();
		AwsCredentials credentials = AwsBasicCredentials.create(awsAccessKey, secretAccessKey);
        Region region = Region.AP_SOUTH_1;
        int noOfFilesUploaded = 0;
        S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(region)
                .build();
        
        try {
            S3TransferManager transferManager = S3TransferManager.builder()
                    .s3Client(s3AsyncClient)
                    .build();
            
            DirectoryUpload directoryUpload = transferManager.uploadDirectory(UploadDirectoryRequest.builder()
                    .source(Paths.get(localLocation))
                    .bucket(BUCKET_NAME).s3Prefix(bucketLocation)
                    .build());
            long noOfFiles = Files.walk(Paths.get(localLocation))
                    .filter(p -> p.toFile().isFile())
                    .mapToLong(p -> p.toFile().length())
                    .sum();
            CompletedDirectoryUpload completedDirectoryUpload = directoryUpload.completionFuture().join();
            completedDirectoryUpload.failedTransfers()
                    .forEach(fail -> LOGGER.warn("Object [{}] failed to transfer", fail.toString()));
            noOfFilesUploaded =   (int) noOfFiles - completedDirectoryUpload.failedTransfers().size();
            int noOfFilesfailed =  completedDirectoryUpload.failedTransfers().size();
            LOGGER.info("Successfully placed " + noOfFilesUploaded + " files into bucket " + BUCKET_NAME);
            LOGGER.info("Failed to place " + noOfFilesfailed + " files into bucket " + BUCKET_NAME);

        } catch (Exception ex) {
            LOGGER.error("S3 exception occured while uploading object.",ex);
        }finally{
        	Instant end = Instant.now();
        	Duration timeTaken = Duration.between(start,end);
        	LOGGER.info("Time taken to upload the files to S3: "+ timeTaken.toString() + " ms");
        	s3AsyncClient.close();
        }
		return noOfFilesUploaded;
	}


	/**
	 * Method to add current upload's uniqueId into a common shared queue,
	 * for Deployment service to pick the newly uploaded project.
	 * @param uniqueId
	 * @return
	 */
	public static Boolean addProcessToQueue(String uniqueId) {
		LOGGER.info("Entered inside addProcessToQueue().");
		Boolean isSaved = Boolean.FALSE;
		try (Jedis jedis = new JedisPool(redisUrl,redisPort).getResource()){
			jedis.lpush(BUCKET_NAME, uniqueId);
			isSaved = Boolean.TRUE;
		}catch (Exception ex) {
			LOGGER.error("Exception occured in addProcessToQueue(): ", ex);
		}
		return isSaved;
	}


	/**
	 * Fetches path to credential.properties from environment variable and initialize the credentials 
	 * for Database, S3 etc.<br>
	 * Add an Environment variable in launch configurations as <b>CREDS_VAL</b> having value as path 
	 * to <b>credentials.properties</b> file.
	 */
	public static void initilizeCredentials() {
		LOGGER.info("Entered inside initilizeCredentials().");
		String credsPath = (StringUtils.isBlank(System.getenv(CREDS_FILE_KEY)))
				?System.getProperty(CREDS_FILE_KEY):System.getenv(CREDS_FILE_KEY);
		LOGGER.info("Credentials file path : " + credsPath);
		if(StringUtils.isNotBlank(credsPath)) {
			FileInputStream credentialsReader = null;
			try {
				credentialsReader = new FileInputStream(credsPath + CREDS_FILE_NAME);
				if(null != credentialsReader) properties.load(credentialsReader);
			}catch(Exception ex) {
				LOGGER.error("Exception occured while initiliazing credentials. ", ex);
			}finally {
				closeInputStream(credentialsReader);
			}
			
			awsAccessKey = properties.getProperty(S3_ACCESS_KEY);
			secretAccessKey = properties.getProperty(S3_SECRET_ACCESS_KEY);
			redisUrl = properties.getProperty(REDIS_URL);
			redisPort = (null != properties.getProperty(REDIS_PORT))
					?Integer.parseInt(properties.getProperty(REDIS_PORT)):redisPort;
			keyLen = (null != properties.getProperty(KEY_LENGTH))
					?Integer.parseInt(properties.getProperty(KEY_LENGTH)):keyLen;
			return;
		}
		LOGGER.info("Unable to fetch credentials.properties file path from env variable.");
	}
	
	
	/**
	 * static method to close input stream if not null.
	 * @param fileInputStream
	 */
	private static void closeInputStream(FileInputStream fileInputStream) {
		try {
			if(null != fileInputStream) {
				fileInputStream.close();
			}
		} catch(Exception ex) {
			LOGGER.error("Exception occured while closing the fileInputStream.",ex);
		}
	}
	
}