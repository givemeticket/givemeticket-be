package kr.givemeticket.api.login.domain;

import java.security.interfaces.RSAPublicKey;

public interface OidcPublicKeyProvider {

    RSAPublicKey getPublicKey(String keyId);
}
