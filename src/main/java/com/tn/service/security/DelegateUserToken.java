package com.tn.service.security;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * A service acting for a user passes that user's access token (the raw JWT) to another service in {@link #HEADER}. The
 * receiving service identifies the user only by verifying it with {@link AccessTokenVerifier}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DelegateUserToken
{
  public static final String HEADER = "X-Delegate-User-Token";
}
