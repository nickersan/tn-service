package com.tn.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class AccessTokenVerifierTest
{
  private static final String ISSUER = "tn";
  private static final String SUBJECT = "891661201065939929";
  private static final String ACCESS = "access";
  private static final KeyPair KEY_PAIR = Jwts.SIG.RS256.keyPair().build();
  private static final KeyPair OTHER_KEY_PAIR = Jwts.SIG.RS256.keyPair().build();

  private final AccessTokenVerifier accessTokenVerifier = new AccessTokenVerifier(KEY_PAIR.getPublic(), ISSUER);

  @Test
  void shouldReturnTheSubjectOfAValidAccessToken()
  {
    String token = token(KEY_PAIR, ISSUER, SUBJECT, ACCESS, inTenMinutes());

    assertEquals(Optional.of(SUBJECT), accessTokenVerifier.subject(token));
    assertEquals(SUBJECT, accessTokenVerifier.subjectRequired(token));
  }

  @Test
  void shouldAcceptABase64PublicKeyIncludingWhitespace() throws Exception
  {
    String publicKey = Base64.getMimeEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());

    assertEquals(SUBJECT, new AccessTokenVerifier(publicKey, ISSUER).subjectRequired(token(KEY_PAIR, ISSUER, SUBJECT, ACCESS, inTenMinutes())));
  }

  @Test
  void shouldReturnNoSubjectForAValidAccessTokenWithoutOne()
  {
    assertEquals(Optional.empty(), accessTokenVerifier.subject(token(KEY_PAIR, ISSUER, null, ACCESS, inTenMinutes())));
    assertEquals(Optional.empty(), accessTokenVerifier.subject(token(KEY_PAIR, ISSUER, " ", ACCESS, inTenMinutes())));
  }

  @Test
  void shouldRejectAValidAccessTokenWithoutASubjectWhenOneIsRequired()
  {
    String token = token(KEY_PAIR, ISSUER, null, ACCESS, inTenMinutes());

    assertRejected(() -> accessTokenVerifier.subjectRequired(token));
  }

  @Test
  void shouldRejectARefreshToken()
  {
    assertRejected(token(KEY_PAIR, ISSUER, SUBJECT, "refresh", inTenMinutes()));
  }

  @Test
  void shouldRejectATokenWithoutATokenUse()
  {
    assertRejected(token(KEY_PAIR, ISSUER, SUBJECT, null, inTenMinutes()));
  }

  @Test
  void shouldRejectATokenWhoseTokenUseIsNotAString()
  {
    assertRejected(token(KEY_PAIR, ISSUER, SUBJECT, 1, inTenMinutes()));
  }

  @Test
  void shouldRejectAnExpiredToken()
  {
    assertRejected(token(KEY_PAIR, ISSUER, SUBJECT, ACCESS, Instant.now().minus(1, ChronoUnit.MINUTES)));
  }

  @Test
  void shouldRejectATokenSignedByAnotherKey()
  {
    assertRejected(token(OTHER_KEY_PAIR, ISSUER, SUBJECT, ACCESS, inTenMinutes()));
  }

  @Test
  void shouldRejectATokenFromAnotherIssuer()
  {
    assertRejected(token(KEY_PAIR, "someone-else", SUBJECT, ACCESS, inTenMinutes()));
  }

  @Test
  void shouldRejectAMissingOrMalformedToken()
  {
    assertRejected((String)null);
    assertRejected(" ");
    assertRejected("not-a-jwt");
  }

  private void assertRejected(String token)
  {
    assertRejected(() -> accessTokenVerifier.subject(token));
    assertRejected(() -> accessTokenVerifier.subjectRequired(token));
  }

  private void assertRejected(Executable verification)
  {
    assertNull(assertThrows(InvalidAccessTokenException.class, verification).getCause());
  }

  private Instant inTenMinutes()
  {
    return Instant.now().plus(10, ChronoUnit.MINUTES);
  }

  private String token(KeyPair keyPair, String issuer, String subject, Object tokenUse, Instant expiration)
  {
    return Jwts.builder()
      .subject(subject)
      .issuer(issuer)
      .claim("token_use", tokenUse)
      .expiration(Date.from(expiration))
      .signWith(keyPair.getPrivate())
      .compact();
  }
}
