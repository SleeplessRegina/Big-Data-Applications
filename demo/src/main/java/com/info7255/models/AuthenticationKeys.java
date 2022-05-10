package com.info7255.models;

import java.security.PrivateKey;
import java.security.PublicKey;

public class AuthenticationKeys {

	private PublicKey publicKey;
	private PrivateKey privateKey;

	public AuthenticationKeys(PublicKey publicKey, PrivateKey privateKey) {
		super();
		this.publicKey = publicKey;
		this.privateKey = privateKey;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

}