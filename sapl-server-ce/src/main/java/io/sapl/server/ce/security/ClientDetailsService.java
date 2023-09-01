/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.server.ce.security;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.heutelbeck.uuid.Base64Id;

import io.sapl.server.ce.model.clients.ClientCredentials;
import io.sapl.server.ce.model.clients.ClientCredentialsRepository;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientDetailsService implements UserDetailsService {

	public static final String	CLIENT	= "CLIENT";
	public static final String	ADMIN	= "ADMIN";

	@Value("${io.sapl.server.accesscontrol.admin-username:#{null}}")
	private String								adminUsername;
	@Value("${io.sapl.server.accesscontrol.encoded-admin-password:#{null}}")
	private String								encodedAdminPassword;
	private final ClientCredentialsRepository	clientCredentialsRepository;
	private final PasswordEncoder				passwordEncoder;

	@PostConstruct
	void validateSecuritySettings() {
		if (adminUsername == null) {
			log.error(
					"Admin username undefined. To define the username, specify it in the property 'io.sapl.server.accesscontrol.admin-username'.");
		}
		if (encodedAdminPassword == null) {
			log.error(
					"Admin password undefined. To define the password, specify it in the property 'io.sapl.server.accesscontrol.encoded-admin-password'. The password is expected in encoded form using BCrypt.");
		}
		if (adminUsername == null || encodedAdminPassword == null) {
			throw new IllegalStateException("Administrator credentials missing.");
		}
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		if (adminUsername.equals(username)) {
			return org.springframework.security.core.userdetails.User.withUsername(adminUsername)
					.password(encodedAdminPassword).roles(ADMIN).build();
		}

		var clientCredentials = clientCredentialsRepository.findByKey(username)
				.orElseThrow(() -> new UsernameNotFoundException(
						String.format("client credentials with key \"%s\" not found", username)));

		return org.springframework.security.core.userdetails.User.withUsername(clientCredentials.getKey())
				.password(clientCredentials.getEncodedSecret()).roles(CLIENT).build();
	}

	public Collection<ClientCredentials> getAll() {
		return clientCredentialsRepository.findAll();
	}

	public long getAmount() {
		return clientCredentialsRepository.count();
	}

	public Tuple2<ClientCredentials, String> createDefault() {
		var	key					= Base64Id.randomID();
		var	secret				= Base64Id.randomID();
		var	clientCredentials	= clientCredentialsRepository.save(new ClientCredentials(key, encodeSecret(secret)));
		return Tuples.of(clientCredentials, secret);
	}

	public void delete(@NonNull Long id) {
		clientCredentialsRepository.deleteById(id);
	}

	public String encodeSecret(@NonNull String secret) {
		return passwordEncoder.encode(secret);
	}

}