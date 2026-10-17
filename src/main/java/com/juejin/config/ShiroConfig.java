package com.juejin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juejin.mapper.SysUserMapper;
import com.juejin.security.JwtFilter;
import com.juejin.security.JwtRealm;
import com.juejin.security.JwtUtil;
import com.juejin.security.StatelessSubjectFactory;
import com.juejin.security.TokenStore;
import jakarta.servlet.Filter;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.servlet.AbstractShiroFilter;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ShiroConfig {

    @Bean
    public Realm jwtRealm(SysUserMapper sysUserMapper, JwtUtil jwtUtil, TokenStore tokenStore) {
        return new JwtRealm(sysUserMapper, jwtUtil, tokenStore);
    }

    @Bean
    public DefaultWebSecurityManager securityManager(Realm jwtRealm) {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(jwtRealm);
        // 纯 token 认证，不创建 Shiro Session
        securityManager.setSubjectFactory(new StatelessSubjectFactory());
        SecurityUtils.setSecurityManager(securityManager);
        return securityManager;
    }

    @Bean
    public static LifecycleBeanPostProcessor lifecycleBeanPostProcessor() {
        return new LifecycleBeanPostProcessor();
    }

    /**
     * 开启 @RequiresPermissions / @RequiresRoles 等注解鉴权
     */
    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(SecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor advisor = new AuthorizationAttributeSourceAdvisor();
        advisor.setSecurityManager(securityManager);
        return advisor;
    }

    @Bean
    public static DefaultAdvisorAutoProxyCreator advisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
        creator.setProxyTargetClass(true);
        return creator;
    }

    /**
     * 手动注册 Shiro 过滤器，避免引入 shiro-spring-boot-starter 后自动注册
     */
    @Bean
    public FilterRegistrationBean<Filter> shiroFilterRegistration(SecurityManager securityManager,
                                                                 ObjectMapper objectMapper) throws Exception {
        ShiroFilterFactoryBean factoryBean = new ShiroFilterFactoryBean();
        factoryBean.setSecurityManager(securityManager);

        Map<String, Filter> filters = new LinkedHashMap<>();
        filters.put("jwt", new JwtFilter(objectMapper));
        factoryBean.setFilters(filters);

        Map<String, String> chain = new LinkedHashMap<>();
        // 登录、注册、短信验证码无需认证
        chain.put("/api/auth/login", "anon");
        chain.put("/api/auth/register", "anon");
        chain.put("/api/auth/sms/**", "anon");
        // 其余接口需要 JWT
        chain.put("/api/**", "jwt");
        chain.put("/**", "anon");
        factoryBean.setFilterChainDefinitionMap(chain);

        AbstractShiroFilter shiroFilter = (AbstractShiroFilter) factoryBean.getObject();

        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(shiroFilter);
        registration.addUrlPatterns("/*");
        registration.setName("shiroFilter");
        registration.setOrder(1);
        return registration;
    }
}
