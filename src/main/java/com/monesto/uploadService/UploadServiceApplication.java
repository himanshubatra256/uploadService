package com.monesto.uploadService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.monesto.uploadService.utils.UploadServiceUtil;

@SpringBootApplication
public class UploadServiceApplication {

	public static void main(String[] args) {
		UploadServiceUtil.initilizeCredentials();
		SpringApplication.run(UploadServiceApplication.class, args);
	}
}
