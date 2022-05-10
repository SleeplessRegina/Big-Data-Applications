package com.info7255.configuration;

import javax.servlet.Filter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
public class EtagConfiguration {
	 @Bean
	 public Filter shallowEtagHeaderFilter() {
		 return new ShallowEtagHeaderFilter();
	 }	
}
