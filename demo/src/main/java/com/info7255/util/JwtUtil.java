package com.info7255.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import org.springframework.stereotype.Service;

import com.info7255.models.AuthenticationKeys;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtUtil {

	private AuthenticationKeys authticationKeys;

	public JwtUtil(AuthenticationKeys authticationKeys) {
		this.authticationKeys = authticationKeys;
	}

	public String extractUsername(String token) {
		return extractClaim(token, Claims::getSubject);
	}

	public Date extractExpiration(String token) {
		return extractClaim(token, Claims::getExpiration);
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		final Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parser().setSigningKey(authticationKeys.getPublicKey()).parseClaimsJws(token).getBody();
	}

	private Boolean isTokenExpired(String token) {
		return extractExpiration(token).before(new Date());
	}

	public String generateToken() {
		Map<String, Object> claims = new HashMap<>();
		return createToken(claims);
	}

	private String createToken(Map<String, Object> claims) {

		return Jwts.builder().setClaims(claims).setIssuedAt(new Date(System.currentTimeMillis()))
				.setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10))
				.signWith(SignatureAlgorithm.RS256, authticationKeys.getPrivateKey()).compact();
	}

	public Boolean validateToken(String token) {
		return !isTokenExpired(token);
	}
}