/*
 * Copyright 2010-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.build.aws.maven;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import org.apache.maven.wagon.authentication.AuthenticationInfo;

final class AuthenticationInfoAWSCredentialsProviderChain extends AWSCredentialsProviderChain {

    public static final String VALUE_ENVIRONMENT_VARIABLE_CREDENTIALS_PROVIDER = "EnvironmentVariable";
    public static final String VALUE_SYSTEM_PROPERTIES_CREDENTIALS_PROVIDER = "SystemProperties";
    public static final String VALUE_PROFILE_CREDENTIALS_PROVIDER = "Profile";
    public static final String VALUE_INSTANCE_PROFILE_CREDENTIALS_PROVIDER = "InstanceProfile";
    public static final String VALUE_AUTHENTICATION_INFO_CREDENTIALS_PROVIDER = "AuthenticationInfo";
    
    private static final Logger log = LoggerFactory.getLogger(AuthenticationInfoAWSCredentialsProviderChain.class);
    
    private AuthenticationInfoAWSCredentialsProviderChain(AWSCredentialsProvider... credentialsProviders) {
        super(credentialsProviders);
    }
    
    AuthenticationInfoAWSCredentialsProviderChain(AuthenticationInfo authenticationInfo) {
        super(new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                new InstanceProfileCredentialsProvider(false),
                new AuthenticationInfoAWSCredentialsProvider(authenticationInfo));
    }
    
    public static AWSCredentialsProvider buildFromParameterValue(String parameterValue,
            AuthenticationInfo authenticationInfo) {
        if (parameterValue == null) {
            log.debug("using " + DefaultAWSCredentialsProviderChain.class.getCanonicalName());

            return new DefaultAWSCredentialsProviderChain();
        }
        String[] tokens = parameterValue.split("[^\\w]+");
        List<AWSCredentialsProvider> providerList = new ArrayList<AWSCredentialsProvider>();
        for (String token : tokens) {
            AWSCredentialsProvider provider = parseProvider(token, authenticationInfo);
            providerList.add(provider);
        }
        log.debug("building credentials provider chain with {} providers specified by {}", providerList.size(), parameterValue);
        AWSCredentialsProvider[] providerArray = providerList.toArray(new AWSCredentialsProvider[providerList.size()]);
        return new AuthenticationInfoAWSCredentialsProviderChain(providerArray);
    }
    
    private static AWSCredentialsProvider parseProvider(String token, AuthenticationInfo authenticationInfo) {
        final AWSCredentialsProvider provider;
        if (VALUE_ENVIRONMENT_VARIABLE_CREDENTIALS_PROVIDER.equalsIgnoreCase(token)) {
            provider = new EnvironmentVariableCredentialsProvider();
        } else if (VALUE_SYSTEM_PROPERTIES_CREDENTIALS_PROVIDER.equalsIgnoreCase(token)) {
            provider = new SystemPropertiesCredentialsProvider();
        } else if (VALUE_PROFILE_CREDENTIALS_PROVIDER.equalsIgnoreCase(token)) {
            provider = new ProfileCredentialsProvider();
        } else if (VALUE_INSTANCE_PROFILE_CREDENTIALS_PROVIDER.equalsIgnoreCase(token)) {
            provider = new InstanceProfileCredentialsProvider(false);
        } else if (VALUE_AUTHENTICATION_INFO_CREDENTIALS_PROVIDER.equalsIgnoreCase(token)) {
            provider = new AuthenticationInfoAWSCredentialsProvider(authenticationInfo);
        } else {
            throw new IllegalArgumentException("unrecognized credentials provider specification: " + token);
        }
        return provider;
    }
}
