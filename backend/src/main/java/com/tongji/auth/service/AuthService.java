package com.tongji.auth.service;

import lombok.RequiredArgsConstructor;
import com.tongji.auth.api.dto.AuthResponse;
import com.tongji.auth.api.dto.AuthUserResponse;
import com.tongji.auth.api.dto.LoginRequest;
import com.tongji.auth.api.dto.PasswordResetRequest;
import com.tongji.auth.api.dto.RegisterRequest;
import com.tongji.auth.api.dto.SendCodeRequest;
import com.tongji.auth.api.dto.SendCodeResponse;
import com.tongji.auth.api.dto.TokenRefreshRequest;
import com.tongji.auth.api.dto.TokenResponse;
import com.tongji.auth.audit.LoginLogService;
import com.tongji.auth.config.AuthProperties;
import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
import com.tongji.auth.model.ClientInfo;
import com.tongji.auth.model.IdentifierType;
import com.tongji.auth.token.JwtService;
import com.tongji.auth.token.RefreshTokenStore;
import com.tongji.auth.token.TokenPair;
import com.tongji.user.domain.User;
import com.tongji.user.service.UserService;
import com.tongji.auth.util.IdentifierValidator;
import com.tongji.auth.verification.SendCodeResult;
import com.tongji.auth.verification.VerificationCheckResult;
import com.tongji.auth.verification.VerificationCodeStatus;
import com.tongji.auth.verification.VerificationScene;
import com.tongji.auth.verification.VerificationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * 璁よ瘉涓氬姟鏈嶅姟銆?
 * <p>
 * 鑱岃矗锛氬彂閫侀獙璇佺爜銆佹敞鍐屻€佺櫥褰曘€佸埛鏂颁护鐗屻€佺櫥鍑恒€侀噸缃瘑鐮併€佹煡璇㈠綋鍓嶇敤鎴蜂俊鎭€?
 * 瀹夊叏绛栫暐锛?
 * - 璐﹀彿鏍煎紡鏍￠獙锛堟墜鏈哄彿/閭锛夛紱
 * - 楠岃瘉鐮佺姸鎬佹鏌ワ紙杩囨湡/閿欒/灏濊瘯瓒呴檺锛夛紱
 * - 瀵嗙爜澶嶆潅搴︽牎楠岋紙闀垮害涓庡瓧绗︾被鍨嬶級锛?
 * - Refresh Token 鐧藉悕鍗曞瓨鍌ㄤ笌杞崲锛岀櫥鍑?閲嶇疆瀵嗙爜鍚庡け鏁堟棫浠ょ墝锛?
 * 瀹¤锛氳褰曟敞鍐?鐧诲綍鎴愬姛涓庡け璐ワ紝鍖呭惈娓犻亾銆両P銆乁A銆?
 * 浠ょ墝锛氱鍙?RS256 鐨?Access/Refresh JWT锛屾惡甯?uid銆乼oken_type銆乯ti銆?
 * 渚濊禆锛歎serService銆乂erificationService銆丳asswordEncoder銆丣wtService銆丷efreshTokenStore銆丩oginLogService銆丄uthProperties銆?
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final VerificationService verificationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginLogService loginLogService;
    private final AuthProperties authProperties;

    /**
     * 鍙戦€侀獙璇佺爜骞惰繑鍥炶繃鏈熶俊鎭€?
     * <p>
     * 娉ㄥ唽鍦烘櫙瑕佹眰鏍囪瘑涓嶅瓨鍦紱鐧诲綍/閲嶇疆瀵嗙爜鍦烘櫙瑕佹眰鏍囪瘑瀛樺湪銆?
     *
     * @param request 璇锋眰浣擄紝鍖呭惈锛氭爣璇嗙被鍨嬩笌鍊笺€佸満鏅€?
     * @return 鍝嶅簲浣擄紝鍖呭惈鐩爣鏍囪瘑銆佸満鏅笌楠岃瘉鐮佽繃鏈熺鏁般€?
     * @throws BusinessException 褰撴爣璇嗘牸寮忛敊璇垨瀛樺湪鎬т笉绗﹀悎鍦烘櫙瑕佹眰鏃舵姏鍑恒€?
     */
    public SendCodeResponse sendCode(SendCodeRequest request) {
        validateIdentifier(request.identifierType(), request.identifier());
        String normalized = normalizeIdentifier(request.identifierType(), request.identifier());
        boolean exists = identifierExists(request.identifierType(), normalized);
        if (request.scene() == VerificationScene.REGISTER && exists) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS);
        }
        if ((request.scene() == VerificationScene.LOGIN || request.scene() == VerificationScene.RESET_PASSWORD) && !exists) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);
        }
        SendCodeResult result = verificationService.sendCode(request.scene(), normalized);
        return new SendCodeResponse(result.identifier(), result.scene(), result.expireSeconds());
    }

    /**
     * 娉ㄥ唽鐢ㄦ埛骞剁鍙戜护鐗屻€?
     * <p>
     * 楠岃瘉鏍囪瘑涓庨獙璇佺爜锛屽垱寤虹敤鎴凤紙鍙€夎缃瘑鐮侊級锛岃褰曞璁★紝绛惧彂浠ょ墝瀵瑰苟淇濆瓨鍒锋柊浠ょ墝鐧藉悕鍗曘€?
     *
     * @param request    娉ㄥ唽璇锋眰锛屽寘鍚細鏍囪瘑绫诲瀷涓庡€笺€侀獙璇佺爜銆佸彲閫夊瘑鐮併€佹槸鍚﹀悓鎰忓崗璁€?
     * @param clientInfo 瀹㈡埛绔俊鎭紙IP/UA锛夛紝鐢ㄤ簬鐧诲綍瀹¤銆?
     * @return 璁よ瘉鍝嶅簲锛屽寘鍚敤鎴蜂俊鎭笌浠ょ墝瀵广€?
     * @throws BusinessException 褰撴湭鍚屾剰鍗忚銆佹爣璇嗗啿绐併€侀獙璇佺爜澶辫触銆佸瘑鐮佷笉鍚堣鏃舵姏鍑恒€?
     */
    public AuthResponse register(RegisterRequest request, ClientInfo clientInfo) {
        if (!request.agreeTerms()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_ACCEPTED);
        }
        validateIdentifier(request.identifierType(), request.identifier());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());
        if (identifierExists(request.identifierType(), identifier)) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS);
        }
        ensureVerificationSuccess(verificationService.verify(VerificationScene.REGISTER, identifier, request.code()));

        User user = User.builder()
                .phone(request.identifierType() == IdentifierType.PHONE ? identifier : null)
                .email(request.identifierType() == IdentifierType.EMAIL ? identifier : null)
                .nickname(generateNickname())
                .avatar("https://static.xingzhiquan.cn/default-avatar.png")
                .bio(null)
                .tagsJson("[]")
                .build();

        if (StringUtils.hasText(request.password())) {
            validatePassword(request.password());
            user.setPasswordHash(passwordEncoder.encode(request.password().trim()));
        }

        userService.createUser(user);
        TokenPair tokenPair = jwtService.issueTokenPair(user);
        storeRefreshToken(user.getId(), tokenPair);
        loginLogService.record(user.getId(), identifier, "REGISTER", clientInfo.ip(), clientInfo.userAgent(), "SUCCESS");

        return new AuthResponse(mapUser(user), mapToken(tokenPair));
    }

    /**
     * 鐧诲綍骞剁鍙戜护鐗屻€?
     * <p>
     * 鏀寔瀵嗙爜鎴栭獙璇佺爜閫氶亾锛涙垚鍔熷悗璁板綍瀹¤锛岀鍙戜护鐗屽骞朵繚瀛樺埛鏂颁护鐗岀櫧鍚嶅崟銆?
     *
     * @param request    鐧诲綍璇锋眰锛屽寘鍚細鏍囪瘑绫诲瀷涓庡€笺€佸瘑鐮佹垨楠岃瘉鐮侊紙浜岄€変竴锛夈€?
     * @param clientInfo 瀹㈡埛绔俊鎭紙IP/UA锛夛紝鐢ㄤ簬鐧诲綍瀹¤銆?
     * @return 璁よ瘉鍝嶅簲锛屽寘鍚敤鎴蜂俊鎭笌浠ょ墝瀵广€?
     * @throws BusinessException 褰撶敤鎴蜂笉瀛樺湪銆佸嚟璇侀敊璇垨璇锋眰涓嶅悎娉曟椂鎶涘嚭銆?
     */
    public AuthResponse login(LoginRequest request, ClientInfo clientInfo) {
        validateIdentifier(request.identifierType(), request.identifier());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());
        Optional<User> userOptional = findUserByIdentifier(request.identifierType(), identifier);
        if (userOptional.isEmpty()) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);
        }
        User user = userOptional.get();
        String channel;
        if (StringUtils.hasText(request.password())) {
            channel = "PASSWORD";
            if (!StringUtils.hasText(user.getPasswordHash()) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                loginLogService.record(user.getId(), identifier, channel, clientInfo.ip(), clientInfo.userAgent(), "FAILED");
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
        } else if (StringUtils.hasText(request.code())) {
            channel = "CODE";
            ensureVerificationSuccess(verificationService.verify(VerificationScene.LOGIN, identifier, request.code()));
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供验证码或密码");
        }
        TokenPair tokenPair = jwtService.issueTokenPair(user);
        storeRefreshToken(user.getId(), tokenPair);
        loginLogService.record(user.getId(), identifier, channel, clientInfo.ip(), clientInfo.userAgent(), "SUCCESS");
        return new AuthResponse(mapUser(user), mapToken(tokenPair));
    }

    /**
     * 浣跨敤鍒锋柊浠ょ墝鑾峰彇鏂扮殑浠ょ墝瀵广€?
     * <p>
     * 鏍￠獙鍒锋柊浠ょ墝绫诲瀷涓庣櫧鍚嶅崟鏈夋晥鎬э紝绛惧彂鏂颁护鐗屽悗鎾ら攢鏃у埛鏂颁护鐗屽苟瀛樺偍鏂颁护鐗屻€?
     *
     * @param request 鍒锋柊璇锋眰锛屽寘鍚細refreshToken銆?
     * @return 鏂扮殑浠ょ墝鍝嶅簲銆?
     * @throws BusinessException 褰撳埛鏂颁护鐗屾棤鏁堟垨鐢ㄦ埛涓嶅瓨鍦ㄦ椂鎶涘嚭銆?
     */
    public TokenResponse refresh(TokenRefreshRequest request) {
        Jwt jwt = decodeRefreshToken(request.refreshToken());

        if (!Objects.equals("refresh", jwtService.extractTokenType(jwt))) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        long userId = jwtService.extractUserId(jwt);
        String tokenId = jwtService.extractTokenId(jwt);

        if (!refreshTokenStore.isTokenValid(userId, tokenId)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        User user = findUserById(userId).orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        TokenPair tokenPair = jwtService.issueTokenPair(user);
        refreshTokenStore.revokeToken(userId, tokenId);
        storeRefreshToken(userId, tokenPair);

        return mapToken(tokenPair);
    }

    /**
     * 鐧诲嚭锛氭挙閿€鎸囧畾鍒锋柊浠ょ墝銆?
     *
     * @param refreshToken 鍒锋柊浠ょ墝瀛楃涓诧紱鑻ヨВ鏋愪负鍚堟硶鍒锋柊浠ょ墝鍒欐挙閿€鍏剁櫧鍚嶅崟璁板綍銆?
     */
    public void logout(String refreshToken) {
        decodeRefreshTokenSafely(refreshToken).ifPresent(jwt -> {
            if (Objects.equals("refresh", jwtService.extractTokenType(jwt))) {
                long userId = jwtService.extractUserId(jwt);
                String tokenId = jwtService.extractTokenId(jwt);
                refreshTokenStore.revokeToken(userId, tokenId);
            }
        });
    }

    /**
     * 浣跨敤楠岃瘉鐮侀噸缃瘑鐮佸苟浣垮埛鏂颁护鐗屽け鏁堛€?
     *
     * @param request 閲嶇疆璇锋眰锛屽寘鍚細鏍囪瘑绫诲瀷涓庡€笺€侀獙璇佺爜銆佹柊瀵嗙爜銆?
     * @throws BusinessException 褰撴爣璇嗕笉瀛樺湪銆侀獙璇佺爜澶辫触鎴栧瘑鐮佺瓥鐣ヤ笉婊¤冻鏃舵姏鍑恒€?
     */
    public void resetPassword(PasswordResetRequest request) {
        validateIdentifier(request.identifierType(), request.identifier());
        validatePassword(request.newPassword());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());
        User user = findUserByIdentifier(request.identifierType(), identifier)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        ensureVerificationSuccess(verificationService.verify(VerificationScene.RESET_PASSWORD, identifier, request.code()));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword().trim()));
        userService.updatePassword(user);
        refreshTokenStore.revokeAll(user.getId());
    }

    /**
     * 鏌ヨ鐢ㄦ埛姒傝淇℃伅銆?
     *
     * @param userId 鐢ㄦ埛 ID銆?
     * @return 鐢ㄦ埛姒傝鍝嶅簲銆?
     * @throws BusinessException 褰撶敤鎴蜂笉瀛樺湪鏃舵姏鍑恒€?
     */
    public AuthUserResponse me(long userId) {
        User user = findUserById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        return mapUser(user);
    }

    /**
     * 淇濊瘉楠岃瘉鐮佹牎楠屾垚鍔燂紝鍚﹀垯鎸夌姸鎬佹姏鍑哄搴斾笟鍔″紓甯搞€?
     *
     * @param result 楠岃瘉鐮佹牎楠岀粨鏋溿€?
     */
    private void ensureVerificationSuccess(VerificationCheckResult result) {
        if (result.isSuccess()) {
            return;
        }
        VerificationCodeStatus status = result.status();
        if (status == VerificationCodeStatus.NOT_FOUND || status == VerificationCodeStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        if (status == VerificationCodeStatus.MISMATCH) {
            throw new BusinessException(ErrorCode.VERIFICATION_MISMATCH);
        }
        if (status == VerificationCodeStatus.TOO_MANY_ATTEMPTS) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOO_MANY_ATTEMPTS);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码校验失败");
    }

    /**
     * 鏍￠獙鏍囪瘑锛堟墜鏈哄彿/閭锛夌殑鏍煎紡銆?
     *
     * @param type       鏍囪瘑绫诲瀷锛歅HONE 鎴?EMAIL銆?
     * @param identifier 鏍囪瘑鍊笺€?
     * @throws BusinessException 褰撴牸寮忎笉鍚堟硶鏃舵姏鍑恒€?
     */
    private void validateIdentifier(IdentifierType type, String identifier) {
        if (type == IdentifierType.PHONE && !IdentifierValidator.isValidPhone(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式错误");
        }
        if (type == IdentifierType.EMAIL && !IdentifierValidator.isValidEmail(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "閭鏍煎紡閿欒");
        }
    }

    /**
     * 鏍￠獙瀵嗙爜绛栫暐锛氶潪绌恒€佹渶灏忛暱搴︺€佸繀椤诲寘鍚瓧姣嶅拰鏁板瓧銆?
     *
     * @param password 鏄庢枃瀵嗙爜銆?
     * @throws BusinessException 褰撳瘑鐮佷笉婊¤冻绛栫暐鏃舵姏鍑恒€?
     */
    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "瀵嗙爜涓嶈兘涓虹┖");
        }
        String trimmed = password.trim();
        if (trimmed.length() < authProperties.getPassword().getMinLength()) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码长度至少" + authProperties.getPassword().getMinLength() + "位");
        }
        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码需包含字母和数字");
        }
    }

    /**
     * 鍒ゆ柇鏍囪瘑鏄惁宸插瓨鍦ㄣ€?
     *
     * @param type       鏍囪瘑绫诲瀷锛歅HONE 鎴?EMAIL銆?
     * @param identifier 鏍囪瘑鍊硷紙闇€涓烘爣鍑嗗寲鏍煎紡锛夈€?
     * @return 鏄惁瀛樺湪銆?
     */
    private boolean identifierExists(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.existsByPhone(identifier);
            case EMAIL -> userService.existsByEmail(identifier);
        };
    }

    /**
     * 鏍规嵁鏍囪瘑鏌ユ壘鐢ㄦ埛銆?
     *
     * @param type       鏍囪瘑绫诲瀷锛歅HONE 鎴?EMAIL銆?
     * @param identifier 鏍囪瘑鍊硷紙闇€涓烘爣鍑嗗寲鏍煎紡锛夈€?
     * @return 鐢ㄦ埛 Optional銆?
     */
    private Optional<User> findUserByIdentifier(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.findByPhone(identifier);
            case EMAIL -> userService.findByEmail(identifier);
        };
    }

    /**
     * 鏍规嵁 ID 鏌ユ壘鐢ㄦ埛銆?
     *
     * @param userId 鐢ㄦ埛 ID銆?
     * @return 鐢ㄦ埛 Optional銆?
     */
    private Optional<User> findUserById(long userId) {
        return userService.findById(userId);
    }

    /**
     * 鏍囧噯鍖栨爣璇嗘枃鏈細鎵嬫満鍙峰幓绌烘牸銆侀偖绠辫浆灏忓啓骞跺幓绌烘牸銆?
     *
     * @param type       鏍囪瘑绫诲瀷锛歅HONE 鎴?EMAIL銆?
     * @param identifier 鍘熷鏍囪瘑鏂囨湰銆?
     * @return 鏍囧噯鍖栧悗鐨勬爣璇嗘枃鏈€?
     */
    private String normalizeIdentifier(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> identifier.trim();
            case EMAIL -> identifier.trim().toLowerCase(Locale.ROOT);
        };
    }

    /**
     * 瀛樺偍鍒锋柊浠ょ墝鐧藉悕鍗曡褰曘€?
     *
     * @param userId    鐢ㄦ埛 ID銆?
     * @param tokenPair 浠ょ墝瀵癸紙鍚埛鏂颁护鐗?ID 涓庤繃鏈熸椂闂达級銆?
     */
    private void storeRefreshToken(Long userId, TokenPair tokenPair) {
        Duration ttl = Duration.between(Instant.now(), tokenPair.refreshTokenExpiresAt());
        if (ttl.isNegative()) {
            ttl = Duration.ZERO;
        }
        refreshTokenStore.storeToken(userId, tokenPair.refreshTokenId(), ttl);
    }

    /**
     * 鏄犲皠鐢ㄦ埛瀹炰綋鍒板搷搴斿璞°€?
     *
     * @param user 鐢ㄦ埛瀹炰綋銆?
     * @return 鐢ㄦ埛鍝嶅簲銆?
     */
    private AuthUserResponse mapUser(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatar(),
                user.getPhone(),
                user.getZgId(),
                user.getBirthday(),
                user.getSchool(),
                user.getBio(),
                user.getGender(),
                user.getTagsJson()
        );
    }

    /**
     * 鏄犲皠浠ょ墝瀵瑰埌鍝嶅簲瀵硅薄銆?
     *
     * @param tokenPair 浠ょ墝瀵广€?
     * @return 浠ょ墝鍝嶅簲銆?
     */
    private TokenResponse mapToken(TokenPair tokenPair) {
        return new TokenResponse(tokenPair.accessToken(), tokenPair.accessTokenExpiresAt(), tokenPair.refreshToken(), tokenPair.refreshTokenExpiresAt());
    }

    /**
     * 鐢熸垚榛樿鏄电О銆?
     *
     * @return 闅忔満鏄电О瀛楃涓层€?
     */
    private String generateNickname() {
        return "星知圈用户" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 瑙ｇ爜鍒锋柊浠ょ墝锛屽け璐ユ椂鎶涗笟鍔″紓甯搞€?
     *
     * @param refreshToken 鍒锋柊浠ょ墝瀛楃涓层€?
     * @return 瑙ｆ瀽寰楀埌鐨?JWT銆?
     * @throws BusinessException 褰撳埛鏂颁护鐗屾棤娉曡В鏋愭椂鎶涘嚭銆?
     */
    private Jwt decodeRefreshToken(String refreshToken) {
        try {
            return jwtService.decode(refreshToken);
        } catch (JwtException ex) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    /**
     * 瀹夊叏瑙ｇ爜鍒锋柊浠ょ墝锛屽け璐ユ椂杩斿洖绌?Optional銆?
     *
     * @param refreshToken 鍒锋柊浠ょ墝瀛楃涓层€?
     * @return 鎴愬姛鏃惰繑鍥?JWT锛屽け璐ユ椂杩斿洖 Optional.empty()銆?
     */
    private Optional<Jwt> decodeRefreshTokenSafely(String refreshToken) {
        try {
            return Optional.of(jwtService.decode(refreshToken));
        } catch (JwtException ex) {
            return Optional.empty();
        }
    }
}
