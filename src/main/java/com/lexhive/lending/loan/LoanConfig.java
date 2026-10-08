package com.lexhive.lending.loan;

import com.lexhive.lending.config.LoanPolicyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class LoanConfig {

    @Bean
    LendingPolicy lendingPolicy(LoanPolicyProperties properties) {
        return new LendingPolicy(properties);
    }
}
