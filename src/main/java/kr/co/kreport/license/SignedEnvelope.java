package kr.co.kreport.license;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 내용과 서명을 함께 담아 오가는 봉투.
 *
 * <h2>왜 비대칭 서명인가</h2>
 * <p>HMAC 같은 대칭키를 쓰면 검증하는 쪽도 같은 키를 갖는다. 그 키는 고객사 서버 안에
 * 있어야 하므로, 그것을 꺼내면 <b>라이선스를 스스로 발급할 수 있다.</b> 비대칭이면
 * 고객사에는 공개키만 들어가고 개인키는 발급자에게만 남는다. 공개키로는 검증만 되고
 * 새 라이선스를 만들 수 없다.</p>
 *
 * <p>Ed25519 를 쓴다. 자바 15부터 표준이라 추가 의존성이 없고, 서명이 64바이트로 짧아
 * 라이선스 파일이 한 화면에 들어온다.</p>
 *
 * <p>담기는 형태는 {@code <base64 본문>.<base64 서명>} 한 줄이다. 점 하나로 가르므로
 * 파일을 메일로 주고받다 줄바꿈이 섞여도 복구할 수 있다.</p>
 */
public final class SignedEnvelope {

    public static final String ALGORITHM = "Ed25519";

    private static final Base64.Encoder ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private SignedEnvelope() {
    }

    /** 본문에 서명해 한 줄로 만든다 */
    public static String seal(byte[] payload, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(payload);
            return ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(signature.sign());
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new IllegalStateException("서명하지 못했습니다.", e);
        }
    }

    /**
     * 서명을 확인하고 본문을 꺼낸다.
     *
     * @throws LicenseException 형식이 틀렸거나 서명이 맞지 않을 때
     */
    public static byte[] open(String envelope, PublicKey publicKey) {
        if (envelope == null || envelope.isBlank()) {
            throw new LicenseException("내용이 비어 있습니다.");
        }
        String compact = envelope.replaceAll("\\s", "");
        int dot = compact.lastIndexOf('.');
        if (dot <= 0 || dot == compact.length() - 1) {
            throw new LicenseException("형식이 올바르지 않습니다. 파일이 잘렸을 수 있습니다.");
        }
        byte[] payload;
        byte[] sign;
        try {
            payload = DECODER.decode(compact.substring(0, dot));
            sign = DECODER.decode(compact.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw new LicenseException("내용을 읽을 수 없습니다. 파일이 손상되었습니다.");
        }
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(payload);
            if (!signature.verify(sign)) {
                // 한 글자만 고쳐도 여기로 온다. 무엇이 바뀌었는지는 알려 주지 않는다.
                throw new LicenseException("서명이 맞지 않습니다. 내용이 변조되었습니다.");
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new LicenseException("서명을 확인하지 못했습니다: " + e.getMessage());
        }
        return payload;
    }

    public static PublicKey publicKey(String base64) {
        try {
            return KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(decodeKey(base64)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("공개키를 읽지 못했습니다.", e);
        }
    }

    public static PrivateKey privateKey(String base64) {
        try {
            return KeyFactory.getInstance(ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(decodeKey(base64)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("개인키를 읽지 못했습니다.", e);
        }
    }

    public static String encodeKey(byte[] encoded) {
        return ENCODER.encodeToString(encoded);
    }

    /** 키 문자열은 설정 파일을 거치며 줄바꿈이나 공백이 섞이기 쉬워 털어 낸다 */
    private static byte[] decodeKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("키가 비어 있습니다.");
        }
        return Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String utf8(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
