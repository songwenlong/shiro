/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.shiro.web.env;

import org.apache.shiro.config.ConfigurationException;
import org.apache.shiro.config.Ini;
import org.apache.shiro.ini.IniFactorySupport;
import org.apache.shiro.lang.io.ResourceUtils;
import org.apache.shiro.lang.util.Destroyable;
import org.apache.shiro.lang.util.Factory;
import org.apache.shiro.lang.util.Initializable;
import org.apache.shiro.lang.util.StringUtils;
import org.apache.shiro.util.CollectionUtils;
import org.apache.shiro.web.config.IniFilterChainResolverFactory;
import org.apache.shiro.web.config.WebIniSecurityManagerFactory;
import org.apache.shiro.web.filter.mgt.FilterChainResolver;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link WebEnvironment} implementation configured by an {@link Ini} instance or {@code Ini} resource locations.
 *
 * @since 1.2
 */
public class IniWebEnvironment extends ResourceBasedWebEnvironment implements Initializable, Destroyable {

    public static final String DEFAULT_WEB_INI_RESOURCE_PATH = "/WEB-INF/shiro.ini";
    public static final String FILTER_CHAIN_RESOLVER_NAME = "filterChainResolver";

    private static final Logger log = LoggerFactory.getLogger(IniWebEnvironment.class);

    /**
     * The Ini that configures this WebEnvironment instance.
     */
    private Ini ini;

    private WebIniSecurityManagerFactory factory;

    public IniWebEnvironment() {
        factory = new WebIniSecurityManagerFactory();
    }

    /**
     * Initializes this instance by resolving any potential (explicit or resource-configured) {@link Ini}
     * configuration and calling {@link #configure() configure} for actual instance configuration.
     */
    public void init() {

        setIni(parseConfig());

        configure();
    }

    /**
     * Loads configuration {@link Ini} from {@link #getConfigLocations()} if set, otherwise falling back
     * to the {@link #getDefaultConfigLocations()}. Finally any Ini objects will be merged with the value returned
     * from {@link #getFrameworkIni()}
     * <p/>
     * 如果设置了 configLocations，则调用 getConfigLocations() 获取 configLocations 来配置 Ini，未设置则调用 getDefaultConfigLocations()。
     * 最后所有 Ini 对象将 与 getFrameworkIni() 返回的值合并。
     * @return Ini configuration to be used by this Environment.
     * @since 1.4
     */
    protected Ini parseConfig() {
        Ini ini = getIni();

        String[] configLocations = getConfigLocations();

        if (log.isWarnEnabled() && !CollectionUtils.isEmpty(ini) &&
                configLocations != null && configLocations.length > 0) {
            log.warn("Explicit INI instance has been provided, but configuration locations have also been " +
                    "specified.  The {} implementation does not currently support multiple Ini config, but this may " +
                    "be supported in the future. Only the INI instance will be used for configuration.",
                    IniWebEnvironment.class.getName());
        }

        if (CollectionUtils.isEmpty(ini)) {
            log.debug("Checking any specified config locations.");
            ini = getSpecifiedIni(configLocations);
        }

        if (CollectionUtils.isEmpty(ini)) {
            log.debug("No INI instance or config locations specified.  Trying default config locations.");
            ini = getDefaultIni();
        }

        // Allow for integrations to provide default that will be merged other configuration.
        // to retain backwards compatibility this must be a different method then 'getDefaultIni()'
        //getFrameworkIni() 返回系统配置，ini为用户配置，将二者合并
        ini = mergeIni(getFrameworkIni(), ini);

        if (CollectionUtils.isEmpty(ini)) {
            String msg = "Shiro INI configuration was either not found or discovered to be empty/unconfigured.";
            throw new ConfigurationException(msg);
        }
        return ini;
    }

    protected void configure() {

        this.objects.clear();

        WebSecurityManager securityManager = createWebSecurityManager();
        setWebSecurityManager(securityManager);

        FilterChainResolver resolver = createFilterChainResolver();
        if (resolver != null) {
            setFilterChainResolver(resolver);
        }
    }

    /**
     * Extension point to allow subclasses to provide an {@link Ini} configuration that will be merged into the
     * users configuration.  The users configuration will override anything set here.
     * <p>
     * 扩展点，以允许子类提供Ini配置，这些配置将合并到用户配置中。用户配置将覆盖这里设置的任何相同名称的内容。
     * <p>
     * <strong>NOTE:</strong> Framework developers should use with caution. It is possible a user could provide
     * configuration that would conflict with the frameworks configuration.  For example: if this method returns an
     * Ini object with the following configuration:
     * <pre><code>
     *     [main]
     *     realm = com.myco.FoobarRealm
     *     realm.foobarSpecificField = A string
     * </code></pre>
     * And the user provides a similar configuration:
     * <pre><code>
     *     [main]
     *     realm = net.differentco.MyCustomRealm
     * </code></pre>
     *
     * This would merge into:
     * <pre><code>
     *     [main]
     *     realm = net.differentco.MyCustomRealm
     *     realm.foobarSpecificField = A string
     * </code></pre>
     *
     * This may cause a configuration error if <code>MyCustomRealm</code> does not contain the field <code>foobarSpecificField</code>.
     * This can be avoided if the Framework Ini uses more unique names, such as <code>foobarRealm</code>. which would result
     * in a merged configuration that looks like:
     * <pre><code>
     *     [main]
     *     foobarRealm = com.myco.FoobarRealm
     *     foobarRealm.foobarSpecificField = A string
     *     realm = net.differentco.MyCustomRealm
     * </code></pre>
     * <p>
     * <strong>注意:</strong> 框架开发人员应该谨慎使用。用户可能会提供与框架冲突的配置。例如:如果此方法返回一个Ini对象，配置如下:
     * <pre><code>
     *     [main]
     *     realm = com.myco.FoobarRealm
     *     realm.foobarSpecificField = A string
     * </code></pre>
     * 用户提供一个相似的配置:
     * <pre><code>
     *     [main]
     *     realm = net.differentco.MyCustomRealm
     * </code></pre>
     *
     * 配置将被合并为:
     * <pre><code>
     *     [main]
     *     realm = net.differentco.MyCustomRealm
     *     realm.foobarSpecificField = A string
     * </code></pre>
     *
     * 如果MyCustomRealm不包含字段foobarSpecificField，就会导致配置错误。
     * Framework Ini使用更特别的唯一的名称，比如foobarRealm，就可以避免这种情况。这样配置合并之后如下:
     * <pre><code>
     *     [main]
     *     foobarRealm = com.myco.FoobarRealm
     *     foobarRealm.foobarSpecificField = A string
     *     realm = net.differentco.MyCustomRealm
     * </code></pre>
     *
     * </p>
     *
     * @return Ini configuration used by the framework integrations.
     * @since 1.4
     */
    protected Ini getFrameworkIni() {
        return null;
    }

    protected Ini getSpecifiedIni(String[] configLocations) throws ConfigurationException {

        Ini ini = null;

        if (configLocations != null && configLocations.length > 0) {

            if (configLocations.length > 1) {
                log.warn("More than one Shiro .ini config location has been specified.  Only the first will be " +
                        "used for configuration as the {} implementation does not currently support multiple " +
                        "files.  This may be supported in the future however.", IniWebEnvironment.class.getName());
            }

            //required, as it is user specified:
            ini = createIni(configLocations[0], true);
        }

        return ini;
    }

    protected Ini mergeIni(Ini ini1, Ini ini2) {

        if (ini1 == null) {
            return ini2;
        }

        if (ini2 == null) {
            return ini1;
        }

        // at this point we have two valid ini objects, create a new one and merge the contents of 2 into 1
        //到这里有两个有效的ini对象，创建一个新对象并将2的内容合并到1
        Ini iniResult = new Ini(ini1);
        iniResult.merge(ini2);

        return iniResult;
    }

    protected Ini getDefaultIni() {

        Ini ini = null;

        String[] configLocations = getDefaultConfigLocations();
        if (configLocations != null) {
            for (String location : configLocations) {
                ini = createIni(location, false);
                //先根据 /WEB-INF/shiro.ini 创建 Ini 对象，创建则返回，未创建继续查找 classpath:shiro.ini
                if (!CollectionUtils.isEmpty(ini)) {
                    log.debug("Discovered non-empty INI configuration at location '{}'.  Using for configuration.",
                            location);
                    break;
                }
            }
        }

        return ini;
    }

    /**
     * Creates an {@link Ini} instance reflecting the specified path, or {@code null} if the path does not exist and
     * is not required.
     * <p/>
     * If the path is required and does not exist or is empty, a {@link ConfigurationException} will be thrown.
     * <p/>
     * 创建一个反映指定路径的 Ini 实例，如果路径不存在且不是必需的，则为null。
     * 如果路径是必需的且不存在或为空，则会抛出ConfigurationException。
     *
     * @param configLocation the resource path to load into an {@code Ini} instance.
     * @param required       if the path must exist and be converted to a non-empty {@link Ini} instance.
     * @return an {@link Ini} instance reflecting the specified path, or {@code null} if the path does not exist and
     *         is not required.
     * @throws ConfigurationException if the path is required but results in a null or empty Ini instance.
     */
    protected Ini createIni(String configLocation, boolean required) throws ConfigurationException {

        Ini ini = null;

        if (configLocation != null) {
            ini = convertPathToIni(configLocation, required);
        }
        if (required && CollectionUtils.isEmpty(ini)) {
            String msg = "Required configuration location '" + configLocation + "' does not exist or did not " +
                    "contain any INI configuration.";
            throw new ConfigurationException(msg);
        }

        return ini;
    }

    protected FilterChainResolver createFilterChainResolver() {

        FilterChainResolver resolver = null;

        Ini ini = getIni();

        if (!CollectionUtils.isEmpty(ini)) {
            Factory<FilterChainResolver> factory = (Factory<FilterChainResolver>) this.objects.get(FILTER_CHAIN_RESOLVER_NAME);
            if (factory instanceof IniFactorySupport) {
                IniFactorySupport iniFactory = (IniFactorySupport) factory;
                iniFactory.setIni(ini);
                iniFactory.setDefaults(this.objects);
            }
            resolver = factory.getInstance();
        }

        return resolver;
    }

    protected WebSecurityManager createWebSecurityManager() {

        Ini ini = getIni();
        if (!CollectionUtils.isEmpty(ini)) {
            factory.setIni(ini);
        }

        Map<String, Object> defaults = getDefaults();
        if (!CollectionUtils.isEmpty(defaults)) {
            factory.setDefaults(defaults);
        }

        WebSecurityManager wsm = (WebSecurityManager)factory.getInstance();

        //SHIRO-306 - get beans after they've been created (the call was before the factory.getInstance() call,
        //which always returned null.
        Map<String, ?> beans = factory.getBeans();
        if (!CollectionUtils.isEmpty(beans)) {
            this.objects.putAll(beans);
        }

        return wsm;
    }

    /**
     * Returns an array with two elements, {@code /WEB-INF/shiro.ini} and {@code classpath:shiro.ini}.
     * <p/>
     * 默认的配置路径
     *
     * @return an array with two elements, {@code /WEB-INF/shiro.ini} and {@code classpath:shiro.ini}.
     */
    protected String[] getDefaultConfigLocations() {
        return new String[]{
                DEFAULT_WEB_INI_RESOURCE_PATH,
                IniFactorySupport.DEFAULT_INI_RESOURCE_PATH
        };
    }

    /**
     * Converts the specified file path to an {@link Ini} instance.
     * <p/>
     * If the path does not have a resource prefix as defined by {@link org.apache.shiro.lang.io.ResourceUtils#hasResourcePrefix(String)}, the
     * path is expected to be resolvable by the {@code ServletContext} via
     * {@link javax.servlet.ServletContext#getResourceAsStream(String)}.
     * <p/>
     * 将指定的文件路径转换为Ini实例。
     * <p/>
     * 如果路径没有ResourceUtils.hasResourcePrefix(String)定义的资源前缀，
     * 该路径将被ServletContext 通过 ServletContext.getresourcestream(String)解析。
     * <p/>
     *
     * @param path     the path of the INI resource to load into an INI instance. 要加载到INI实例中的INI资源的路径
     * @param required if the specified path must exist
     * @return an INI instance populated based on the given INI resource path. 基于给定INI资源路径填充的INI实例。
     */
    private Ini convertPathToIni(String path, boolean required) {

        //TODO - this logic is ugly - it'd be ideal if we had a Resource API to polymorphically encapsulate this behavior

        Ini ini = null;

        if (StringUtils.hasText(path)) {
            InputStream is = null;

            //SHIRO-178: Check for servlet context resource and not only resource paths:
            if (!ResourceUtils.hasResourcePrefix(path)) {
                is = getServletContextResourceStream(path);
            } else {
                try {
                    is = ResourceUtils.getInputStreamForPath(path);
                } catch (IOException e) {
                    if (required) {
                        throw new ConfigurationException(e);
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Unable to load optional path '" + path + "'.", e);
                        }
                    }
                }
            }
            if (is != null) {
                ini = new Ini();
                ini.load(is);
            } else {
                if (required) {
                    throw new ConfigurationException("Unable to load resource path '" + path + "'");
                }
            }
        }

        return ini;
    }

    //TODO - this logic is ugly - it'd be ideal if we had a Resource API to polymorphically encapsulate this behavior
    private InputStream getServletContextResourceStream(String path) {
        InputStream is = null;

        path = WebUtils.normalize(path);
        ServletContext sc = getServletContext();
        if (sc != null) {
            is = sc.getResourceAsStream(path);
        }

        return is;
    }

    /**
     * Returns the {@code Ini} instance reflecting this WebEnvironment's configuration.
     * <p/>
     * 返回反映此 WebEnvironment 配置的 Ini 实例。
     * @return the {@code Ini} instance reflecting this WebEnvironment's configuration.
     */
    public Ini getIni() {
        return this.ini;
    }

    /**
     * Allows for configuration via a direct {@link Ini} instance instead of via
     * {@link #getConfigLocations() config locations}.
     * <p/>
     * If the specified instance is null or empty, the fallback/default resource-based configuration will be used.
     * <p/>
     * 允许通过一个直接的Ini实例来配置，而不是通过配置配置文件路径。
     * <p/>
     * 如果指定的实例为null或空，将使用基于资源的配置
     * <p/>
     *
     * @param ini the ini instance to use for creation.
     */
    public void setIni(Ini ini) {
        this.ini = ini;
    }

    protected Map<String, Object> getDefaults() {
        Map<String, Object> defaults = new HashMap<String, Object>();
        defaults.put(FILTER_CHAIN_RESOLVER_NAME, new IniFilterChainResolverFactory());
        return defaults;
    }

    /**
     * Returns the SecurityManager factory used by this WebEnvironment.
     *
     * @return the SecurityManager factory used by this WebEnvironment.
     * @since 1.4
     */
    @SuppressWarnings("unused")
    protected WebIniSecurityManagerFactory getSecurityManagerFactory() {
        return factory;
    }

    /**
     * Allows for setting the SecurityManager factory which will be used to create the SecurityManager.
     *
     * @param factory the SecurityManager factory to used.
     * @since 1.4
     */
    protected void setSecurityManagerFactory(WebIniSecurityManagerFactory factory) {
        this.factory = factory;
    }
}
