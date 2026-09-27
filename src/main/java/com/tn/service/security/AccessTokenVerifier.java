package com.tn.service.security;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

/**
 * Verifies an access token issued by tn-auth-service against its public key: the signature, expiry, issuer, and a
 * {@code token_use} of {@code access} - a refresh token is never accepted as an access token.
 */
public class AccessTokenVerifier
{
  private static final String KEY_ALGORITHM = "RSA";
  private static final String CLAIM_TOKEN_USE = "token_use";
  private static final String TOKEN_USE_ACCESS = "access";

  private final JwtParser jwtParser;

  public AccessTokenVerifier(PublicKey publicKey, String issuer)
  {
    jwtParser = Jwts.parser()
      .verifyWith(publicKey)
      .requireIssuer(issuer)
      .build();
  }

  /**
   * @param publicKey tn-auth-service's RSA public key, base64 X.509 - whitespace is ignored, so a PEM body can be pasted in
   */
  public AccessTokenVerifier(String publicKey, String issuer) throws NoSuchAlgorithmException, InvalidKeySpecException
  {
    this(publicKey(publicKey), issuer);
  }

  /**
   * @param token the raw JWT, with no {@code Bearer } prefix
   * @return the token's subject, or empty when the token has none
   */
  public Optional<String> subject(String token) throws InvalidAccessTokenException
  {
    return Optional.ofNullable(accessTokenClaims(token).getSubject()).filter(subject -> !subject.isBlank());
  }

  /**
   * @param token the raw JWT, with no {@code Bearer } prefix
   * @return the token's subject
   */
  public String subjectRequired(String token) throws InvalidAccessTokenException
  {
    return subject(token).orElseThrow(() -> new InvalidAccessTokenException("no subject"));
  }

  private static PublicKey publicKey(String publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException
  {
    byte[] decoded = Base64.getDecoder().decode(publicKey.replaceAll("\\s+", ""));

    return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(new X509EncodedKeySpec(decoded));
  }

  private Claims accessTokenClaims(String token) throws InvalidAccessTokenException
  {
    Claims claims = claims(token);
    if (!TOKEN_USE_ACCESS.equals(tokenUse(claims))) throw new InvalidAccessTokenException("not an access token");

    return claims;
  }

  private Claims claims(String token) throws InvalidAccessTokenException
  {
    if (token == null || token.isBlank()) throw new InvalidAccessTokenException("no token");

    try
    {
      return jwtParser.parseSignedClaims(token.trim()).getPayload();
    }
    catch (JwtException | IllegalArgumentException e)
    {
      throw new InvalidAccessTokenException("invalid or expired token");
    }
  }

  private String tokenUse(Claims claims) throws InvalidAccessTokenException
  {
    try
    {
      return claims.get(CLAIM_TOKEN_USE, String.class);
    }
    catch (JwtException e)
    {
      throw new InvalidAccessTokenException("token use is not a string");
    }
  }
}
