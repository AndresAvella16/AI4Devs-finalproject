package com.jobpilot.ai;

import jakarta.validation.Validator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans del módulo de IA. El {@link LlmConcurrencyLimiter} es un singleton: el límite de 3 llamadas simultáneas es
 * global y lo comparten todos los endpoints de IA. {@link StructuredLlmCall} solo existe en modo real, porque
 * necesita el {@link ChatModel} de Anthropic.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmProperties.class)
class LlmConfig {

    @Bean
    PromptTemplates promptTemplates() {
        return new PromptTemplates();
    }

    @Bean
    FakeResponses fakeResponses() {
        return new FakeResponses();
    }

    @Bean
    LlmConcurrencyLimiter llmConcurrencyLimiter(LlmProperties properties) {
        return new LlmConcurrencyLimiter(properties.maxConcurrentCalls(), properties.busyRetryAfterSeconds());
    }

    @Bean
    ProviderErrorTranslator providerErrorTranslator(LlmProperties properties) {
        return new ProviderErrorTranslator(properties.providerRetryAfterSeconds());
    }

    @Bean
    @ConditionalOnProperty(name = "llm.mode", havingValue = "real")
    StructuredLlmCall structuredLlmCall(ChatModel chatModel, PromptTemplates templates,
            LlmConcurrencyLimiter limiter, ProviderErrorTranslator translator, Validator validator) {
        return new StructuredLlmCall(chatModel, templates, limiter, translator, validator);
    }
}
