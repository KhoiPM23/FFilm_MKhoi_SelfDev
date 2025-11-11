package com.example.project.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Bean này định nghĩa tên và đường dẫn của các file properties ngôn ngữ.
     * 'messages' có nghĩa là Spring sẽ tìm các file:
     * - messages.properties (Mặc định)
     * - messages_vi.properties (Tiếng Việt)
     * - messages_ja.properties (Tiếng Nhật)
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        // Tên file gốc (basename) là "messages"
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    /**
     * Bean này quyết định cách lưu trữ lựa chọn ngôn ngữ của người dùng.
     * Chúng ta dùng CookieLocaleResolver để lưu lựa chọn vào cookie.
     * Lần sau người dùng quay lại, trang web sẽ nhớ ngôn ngữ họ đã chọn.
     */
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver();
        // Ngôn ngữ mặc định nếu chưa chọn là Tiếng Việt
        resolver.setDefaultLocale(new Locale("vi"));
        resolver.setCookieName("ffilm-locale-cookie");
        resolver.setCookieMaxAge(3600 * 24 * 30); // 30 ngày
        return resolver;
    }

    /**
     * Bean này (Interceptor) sẽ "bắt" các request có chứa param ?lang=...
     * Đây chính là thứ kích hoạt việc đổi ngôn ngữ khi bạn bấm vào link.
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        // Param trên URL của bạn, vd: /?lang=ja
        lci.setParamName("lang");
        return lci;
    }

    /**
     * Đăng ký Interceptor ở trên với Spring MVC.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}