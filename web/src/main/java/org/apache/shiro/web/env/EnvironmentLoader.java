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
import org.apache.shiro.config.ResourceConfigurable;
import org.apache.shiro.lang.util.ClassUtils;
import org.apache.shiro.lang.util.LifecycleUtils;
import org.apache.shiro.lang.util.StringUtils;
import org.apache.shiro.lang.util.UnknownClassException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;


/**
 * An {@code EnvironmentLoader} is responsible for loading a web application's Shiro {@link WebEnvironment}
 * (which includes the web app's {@link org.apache.shiro.web.mgt.WebSecurityManager WebSecurityManager}) into the
 * {@code ServletContext} at application startup.
 * <p/>
 * In Shiro 1.1 and earlier, the Shiro ServletFilter was responsible for creating the {@code WebSecurityManager} and
 * any additional objects (security filters, etc.).  However, any component not filtered by the Shiro Filter (such
 * as other context listeners) was not able to easily acquire the these objects to perform security operations.
 * <p/>
 * Due to this, in Shiro 1.2 and later, this {@code EnvironmentLoader} (or more likely, the
 * {@link EnvironmentLoaderListener} subclass) is the preferred mechanism to initialize
 * a Shiro environment.  The Shiro Filter, while still required for request filtering, will not perform this
 * initialization at startup if the {@code EnvironmentLoader} (or listener) runs first.
 * <h2>Usage</h2>
 * This implementation will look for two servlet context {@code context-param}s in {@code web.xml}:
 * {@code shiroEnvironmentClass} and {@code shiroConfigLocations} that customize how the {@code WebEnvironment} instance
 * will be initialized.
 * <h3>shiroEnvironmentClass</h3>
 * The {@code shiroEnvironmentClass} {@code context-param}, if it exists, allows you to specify the
 * fully-qualified implementation class name of the {@link WebEnvironment} to instantiate.  For example:
 * <pre>
 * &lt;context-param&gt;
 *     &lt;param-name&gt;shiroEnvironmentClass&lt;/param-name&gt;
 *     &lt;param-value&gt;com.foo.bar.shiro.MyWebEnvironment&lt;/param-value&gt;
 * &lt;/context-param&gt;
 * </pre>
 * If not specified, the default value is the {@link IniWebEnvironment} class, which assumes Shiro's default
 * <a href="http://shiro.apache.org/configuration.html">INI configuration format</a>
 * <h3>shiroConfigLocations</h3>
 * The {@code shiroConfigLocations} {@code context-param}, if it exists, allows you to specify the config location(s)
 * (resource path(s)) that will be relayed to the instantiated {@link WebEnvironment}.  For example:
 * <pre>
 * &lt;context-param&gt;
 *     &lt;param-name&gt;shiroConfigLocations&lt;/param-name&gt;
 *     &lt;param-value&gt;/WEB-INF/someLocation/shiro.ini&lt;/param-value&gt;
 * &lt;/context-param&gt;
 * </pre>
 * The {@code WebEnvironment} implementation must implement the {@link ResourceConfigurable} interface if it is to
 * acquire the {@code shiroConfigLocations} value.
 * <p/>
 * If this {@code context-param} is not specified, the {@code WebEnvironment} instance determines default resource
 * lookup behavior.  For example, the {@link IniWebEnvironment} will check the following two locations for INI config
 * by default (in order):
 * <ol>
 * <li>/WEB-INF/shiro.ini</li>
 * <li>classpath:shiro.ini</li>
 * </ol>
 * <h2>Web Security Enforcement</h2>
 * Using this loader will only initialize Shiro's environment in a web application - it will not filter web requests or
 * perform web-specific security operations.  To do this, you must ensure that you have also configured the
 * {@link org.apache.shiro.web.servlet.ShiroFilter ShiroFilter} in {@code web.xml}.
 * <p/>
 * Finally, it should be noted that this implementation was based on ideas in Spring 3's
 * {@code org.springframework.web.context.ContextLoader} implementation - no need to reinvent the wheel for this common
 * behavior.
 *
 * @see EnvironmentLoaderListener
 * @see org.apache.shiro.web.servlet.ShiroFilter ShiroFilter
 * @since 1.2
 */
public class EnvironmentLoader {

    /**
     * Servlet Context config param for specifying the {@link WebEnvironment} implementation class to use:
     * {@code shiroEnvironmentClass}
     */
    public static final String ENVIRONMENT_CLASS_PARAM = "shiroEnvironmentClass";

    /**
     * Servlet Context config param for the resource path to use for configuring the {@link WebEnvironment} instance:
     * {@code shiroConfigLocations}
     */
    public static final String CONFIG_LOCATIONS_PARAM = "shiroConfigLocations";

    public static final String ENVIRONMENT_ATTRIBUTE_KEY = EnvironmentLoader.class.getName() + ".ENVIRONMENT_ATTRIBUTE_KEY";

    private static final Logger log = LoggerFactory.getLogger(EnvironmentLoader.class);

    /**
     * Initializes Shiro's {@link WebEnvironment} instance for the specified {@code ServletContext} based on the
     * {@link #CONFIG_LOCATIONS_PARAM} value.
     *
     * @param servletContext current servlet context
     * @return the new Shiro {@code WebEnvironment} instance.
     * @throws IllegalStateException if an existing WebEnvironment has already been initialized and associated with
     *                               the specified {@code ServletContext}.
     */
    public WebEnvironment initEnvironment(ServletContext servletContext) throws IllegalStateException {

        if (servletContext.getAttribute(ENVIRONMENT_ATTRIBUTE_KEY) != null) {
            String msg = "There is already a Shiro environment associated with the current ServletContext.  " +
                    "Check if you have multiple EnvironmentLoader* definitions in your web.xml!";
            throw new IllegalStateException(msg);
        }

        servletContext.log("Initializing Shiro environment");
        log.info("Starting Shiro environment initialization.");

        long startTime = System.currentTimeMillis();

        try {

            WebEnvironment environment = createEnvironment(servletContext);
            servletContext.setAttribute(ENVIRONMENT_ATTRIBUTE_KEY,environment);

            log.debug("Published WebEnvironment as ServletContext attribute with name [{}]",
                    ENVIRONMENT_ATTRIBUTE_KEY);

            if (log.isInfoEnabled()) {
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("Shiro environment initialized in {} ms.", elapsed);
            }

            return environment;
        } catch (RuntimeException ex) {
            log.error("Shiro environment initialization failed", ex);
            servletContext.setAttribute(ENVIRONMENT_ATTRIBUTE_KEY, ex);
            throw ex;
        } catch (Error err) {
            log.error("Shiro environment initialization failed", err);
            servletContext.setAttribute(ENVIRONMENT_ATTRIBUTE_KEY, err);
            throw err;
        }
    }

    /**
     * Return the WebEnvironment implementation class to use, either the default
     * {@link IniWebEnvironment} or a custom class if specified.
     *
     * @param servletContext current servlet context
     * @return the WebEnvironment implementation class to use
     * @see #ENVIRONMENT_CLASS_PARAM
     * @see IniWebEnvironment
     * @see #determineWebEnvironment(ServletContext)
     * @see #getDefaultWebEnvironmentClass()
     * @deprecated This method is not longer used by Shiro, and will be removed in future versions,
     * use {@link #determineWebEnvironment(ServletContext)} or {@link #determineWebEnvironment(ServletContext)}
     */
    @Deprecated
    protected Class<?> determineWebEnvironmentClass(ServletContext servletContext) {
        Class<? extends WebEnvironment> webEnvironmentClass = webEnvironmentClassFromServletContext(servletContext);
        if( webEnvironmentClass != null) {
            return webEnvironmentClass;
        } else {

            return getDefaultWebEnvironmentClass();
        }
    }

    private Class<? extends WebEnvironment> webEnvironmentClassFromServletContext(ServletContext servletContext) {

        Class<? extends WebEnvironment> webEnvironmentClass = null;
        //ServletContext 中 ENVIRONMENT_CLASS_PARAM 的参数指定 WebEnvironment 实现类全类名
        String className = servletContext.getInitParameter(ENVIRONMENT_CLASS_PARAM);
        if (className != null) {
            try {
                //加载实现类
                webEnvironmentClass = ClassUtils.forName(className);
            } catch (UnknownClassException ex) {
                throw new ConfigurationException(
                        "Failed to load custom WebEnvironment class [" + className + "]", ex);
            }
        }
        return webEnvironmentClass;
    }

    private WebEnvironment webEnvironmentFromServiceLoader() {

        WebEnvironment webEnvironment = null;
        // try to load WebEnvironment as a service
        // 尝试将 WebEnvironment 作为服务加载
        Iterator<WebEnvironment> iterator = doLoadWebEnvironmentsFromServiceLoader();

        // Use the first one
        // 取第一个
        if (iterator.hasNext()) {
            webEnvironment = iterator.next();
        }
        // if there are others, throw an error
        // 如果 ServiceLoader 加载了多个 WebEnvironment 实现类，抛出异常
        if (iterator.hasNext()) {
            //将所有加载的实现类类名放入 allWebEnvironments，输出日志使用
            List<String> allWebEnvironments = new ArrayList<String>();
            allWebEnvironments.add(webEnvironment.getClass().getName());
            while (iterator.hasNext()) {
                allWebEnvironments.add(iterator.next().getClass().getName());
            }
            throw new ConfigurationException("ServiceLoader for class [" + WebEnvironment.class + "] returned more then one " +
                    "result.  ServiceLoader must return zero or exactly one result for this class. Select one using the " +
                    "servlet init parameter '"+ ENVIRONMENT_CLASS_PARAM +"'. Found: " + allWebEnvironments);
        }
        return webEnvironment;
    }

    protected Iterator<WebEnvironment> doLoadWebEnvironmentsFromServiceLoader() {
        ServiceLoader<WebEnvironment> serviceLoader = ServiceLoader.load(WebEnvironment.class);

        return serviceLoader.iterator();
    }

    /**
     * Returns the default WebEnvironment class, which is unless overridden: {@link IniWebEnvironment}.
     * 返回默认 WebEnvironment 实现类，如果方法没有被重写默认值为 IniWebEnvironment
     * @return the default WebEnvironment class.
     */
    protected Class<? extends WebEnvironment> getDefaultWebEnvironmentClass() {
        return IniWebEnvironment.class;
    }

    /**
     * Return the WebEnvironment implementation class to use, based on the order of:
     * <ul>
     *     <li>A custom WebEnvironment class - specified in the {@code servletContext} {@link #ENVIRONMENT_ATTRIBUTE_KEY} property</li>
     *     <li>{@code ServiceLoader.load(WebEnvironment.class)} - (if more then one instance is found a {@link ConfigurationException} will be thrown</li>
     *     <li>A call to {@link #getDefaultWebEnvironmentClass()} (default: {@link IniWebEnvironment})</li>
     * </ul>
     *
     * 返回 WebEnvironment 的实现类，按如下顺序查找：
     * <ul>
     *     <li>自定义的 WebEnvironment - 用参数 {@code servletContext} {@link #ENVIRONMENT_ATTRIBUTE_KEY} 指定全类名</li>
     *     <li>使用 {@code ServiceLoader.load(WebEnvironment.class)} 加载到的实现类 - (如果查找到多个，抛出异常 {@link ConfigurationException} </li>
     *     <li>调用 {@link #getDefaultWebEnvironmentClass()} 方法获取 (默认实现类 {@link IniWebEnvironment})</li>
     * </ul>
     *
     * @param servletContext current servlet context 当前 servlet context
     * @return the WebEnvironment implementation class to use
     * @see #ENVIRONMENT_CLASS_PARAM
     * @param servletContext the {@code servletContext} to query the {@code ENVIRONMENT_ATTRIBUTE_KEY} property from
     * @return the {@code WebEnvironment} to be used
     */
    protected WebEnvironment determineWebEnvironment(ServletContext servletContext) {

        //1) 从 ServletContext 中获取 WebEnvironment 实现类
        Class<? extends WebEnvironment> webEnvironmentClass = webEnvironmentClassFromServletContext(servletContext);
        WebEnvironment webEnvironment = null;

        // try service loader next
        //2) 尝试 ServiceLoader 加载 WebEnvironment 实现类
        if (webEnvironmentClass == null) {
            webEnvironment = webEnvironmentFromServiceLoader();
        }

        // if webEnvironment is not set, and ENVIRONMENT_CLASS_PARAM prop was not set, use the default
        //3) 上面两步都没有查找到 WebEnvironment 实现类，获取默认实现类
        if (webEnvironmentClass == null && webEnvironment == null) {
            webEnvironmentClass = getDefaultWebEnvironmentClass();
        }

        // at this point, we anything is set for the webEnvironmentClass, load it.
        // 到这里如果为 webEnvironmentClass 设置了值
        if (webEnvironmentClass != null) {
            //实例化
            webEnvironment = (WebEnvironment) ClassUtils.newInstance(webEnvironmentClass);
        }

        return webEnvironment;
    }

    /**
     * Instantiates a {@link WebEnvironment} based on the specified ServletContext.
     * <p/>
     * This implementation {@link #determineWebEnvironmentClass(javax.servlet.ServletContext) determines} a
     * {@link WebEnvironment} implementation class to use.  That class is instantiated, configured, and returned.
     * <p/>
     * This allows custom {@code WebEnvironment} implementations to be specified via a ServletContext init-param if
     * desired.  If not specified, the default {@link IniWebEnvironment} implementation will be used.
     * <p/>
     * 基于指定的 ServletContext 实例化一个 WebEnvironment。
     * <p/>
     * determineWebEnvironmentClass 方法确定了要使用的 WebEnvironment 实现类并实例化、配置、返回。
     * <p/>
     * 这允许在需要的时候通过 ServletContext 初始化参数来指定自定义 WebEnvironment 实现。如果没有指定，将使用默认实现IniWebEnvironment。
     * <p/>
     * @param sc current servlet context
     * @return the constructed Shiro WebEnvironment instance
     * @see MutableWebEnvironment
     * @see ResourceConfigurable
     */
    protected WebEnvironment createEnvironment(ServletContext sc) {

        WebEnvironment webEnvironment = determineWebEnvironment(sc);
        //webEnvironment 必须是可变的，才可以设置属性
        if (!MutableWebEnvironment.class.isInstance(webEnvironment)) {
            throw new ConfigurationException("Custom WebEnvironment class [" + webEnvironment.getClass().getName() +
                    "] is not of required type [" + MutableWebEnvironment.class.getName() + "]");
        }

        //从 ServletContext 中获取 CONFIG_LOCATIONS_PARAM 参数的值
        String configLocations = sc.getInitParameter(CONFIG_LOCATIONS_PARAM);
        //是否为 CONFIG_LOCATIONS_PARAM 配置了值
        boolean configSpecified = StringUtils.hasText(configLocations);

        //如果为 CONFIG_LOCATIONS_PARAM 配置了值，那么 webEnvironment 必须是 ResourceConfigurable，才能设置读取参数值
        if (configSpecified && !(ResourceConfigurable.class.isInstance(webEnvironment))) {
            String msg = "WebEnvironment class [" + webEnvironment.getClass().getName() + "] does not implement the " +
                    ResourceConfigurable.class.getName() + "interface.  This is required to accept any " +
                    "configured " + CONFIG_LOCATIONS_PARAM + "value(s).";
            throw new ConfigurationException(msg);
        }

        //为 WebEnvironment 设置 ServletContext
        MutableWebEnvironment environment = (MutableWebEnvironment) webEnvironment;

        environment.setServletContext(sc);

        //为 WebEnvironment 设置 locations
        if (configSpecified && (environment instanceof ResourceConfigurable)) {
            ((ResourceConfigurable) environment).setConfigLocations(configLocations);
        }

        customizeEnvironment(environment);

        //初始化
        LifecycleUtils.init(environment);

        return environment;
    }

    /**
     * Any additional customization of the Environment can be by overriding this method. For example setup shared
     * resources, etc. By default this method does nothing.
     * <p/>
     * 任何对 Environment 的额外定制都可以通过重写这个方法来完成。例如，设置共享资源等。默认情况下，此方法不做任何操作
     * @param environment
     */
    protected void customizeEnvironment(WebEnvironment environment) {
    }

    /**
     * Destroys the {@link WebEnvironment} for the given servlet context.
     *
     * @param servletContext the ServletContext attributed to the WebSecurityManager
     */
    public void destroyEnvironment(ServletContext servletContext) {
        servletContext.log("Cleaning up Shiro Environment");
        try {
            Object environment = servletContext.getAttribute(ENVIRONMENT_ATTRIBUTE_KEY);
            if (environment instanceof WebEnvironment) {
                finalizeEnvironment((WebEnvironment) environment);
            }
            LifecycleUtils.destroy(environment);
        } finally {
            servletContext.removeAttribute(ENVIRONMENT_ATTRIBUTE_KEY);
        }
    }

    /**
     * Any additional cleanup of the Environment can be done by overriding this method.  For example clean up shared
     * resources, etc. By default this method does nothing.
     * @param environment
     * @since 1.3
     */
    protected void finalizeEnvironment(WebEnvironment environment) {
    }
}
