package com.info7255.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.info7255.models.AuthenticationKeys;

import java.security.*;

@Configuration
public class JwtConfiguration {

	@Bean
	public AuthenticationKeys getKeys() throws NoSuchAlgorithmException {
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(2048);
		KeyPair keyPair = keyPairGenerator.generateKeyPair();
		PublicKey publicKey = keyPair.getPublic();
		PrivateKey privateKey = keyPair.getPrivate();
		return new AuthenticationKeys(publicKey, privateKey);
	}
}