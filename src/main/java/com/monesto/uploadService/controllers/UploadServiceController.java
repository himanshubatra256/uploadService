package com.monesto.uploadService.controllers;

import java.io.BufferedReader;
import java.io.FileReader;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.monesto.uploadService.utils.UploadServiceUtil;

import jakarta.servlet.http.HttpServletRequest;


@RestController
public class UploadServiceController {
	public static final Logger LOGGER = LoggerFactory.getLogger(UploadServiceController.class);
	public static final String SUCCESS = "Success";
	public static final String FAILURE = "Failure";
	public static final String DOWNLOAD_FAIL = "Download Failure !!!";
	public static final String QUEUE_UPDATION_FAILED = "Queue updation Failure !!!";
	
	
	/**
	 * Health check end-point to check Application's running status
	 * @return HttpStatus.OK
	 */
	@GetMapping("/health")
	public ResponseEntity<String> healthCheck(){
		return ResponseEntity.status(HttpStatus.OK).body("Healthy");
	}
	
	
	/**
	 * Generates a uniqueKey, download the GitHub project from input URL and starts with deployment.
	 * @param URL
	 * @return uniqueId
	 */
    @GetMapping("/upload")
    public ResponseEntity<String> uploadAndDownload(@RequestParam String url) {
    	LOGGER.info("Entered into uploadAndDownload with URL: "+ url);
    	String uniqueId = UploadServiceUtil.generateRandomId();
    	if(StringUtils.isBlank(uniqueId) || StringUtils.isBlank(url)) {
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(FAILURE);
    	}
    	
    	Boolean isProjectDownloaded = UploadServiceUtil.getProjectFromURL(url, uniqueId);
    	if(Boolean.FALSE.equals(isProjectDownloaded)) {
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(DOWNLOAD_FAIL);
    	}
    	
    	Boolean isQueueUpdated = UploadServiceUtil.addProcessToQueue(uniqueId);
    	LOGGER.info("Queue updation status: " +  isQueueUpdated);
    	if(Boolean.FALSE.equals(isQueueUpdated)) {
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(QUEUE_UPDATION_FAILED);
    	}
    	
    	return ResponseEntity.status(HttpStatus.OK).body(uniqueId);
    }
    
}
