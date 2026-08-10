package kr.givemeticket.api.user.application;

import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.login.domain.ProviderPrincipal;
import kr.givemeticket.api.login.ui.dto.SignupRequest;
import kr.givemeticket.api.user.domain.User;
import kr.givemeticket.api.user.domain.UserException;
import kr.givemeticket.api.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public boolean isUserExist(String providerId, Provider provider) {
        return userRepository.existsByProviderIdAndProvider(providerId, provider);
    }

    @Transactional(readOnly = true)
    public Long getUserId(String providerId, Provider provider) {
        return userRepository.findByProviderIdAndProvider(providerId, provider)
                .map(User::getId)
                .orElseThrow(UserException::notFound);
    }

    @Transactional
    public Long createUser(SignupRequest request, ProviderPrincipal providerPrincipal) {
        String providerId = providerPrincipal.providerId();
        Provider provider = providerPrincipal.provider();

        if (userRepository.existsByProviderIdAndProvider(providerId, provider)) {
            throw UserException.alreadyRegistered();
        }

        try {
            User user = new User(request.nickname(), request.profileImageUrl(), providerId, provider);
            return userRepository.save(user).getId();
        } catch (DataIntegrityViolationException e) {
            // 같은 계정으로 동시에 가입 요청이 들어온 경우. 위 존재 확인만으로는 못 막는다.
            throw UserException.alreadyRegistered();
        }
    }
}
