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
package org.apache.shiro.aop;

import java.lang.annotation.Annotation;

/**
 * MethodInterceptor that inspects a specific annotation on the method invocation before continuing
 * its execution.
 * </p>
 * The annotation is acquired from the {@link MethodInvocation MethodInvocation} via a
 * {@link AnnotationResolver AnnotationResolver} instance that may be configured.  Unless
 * overridden, the default {@code AnnotationResolver} is a
 * </p>
 * 方法拦截器，在继续执行之前 检查方法调用上的 特定注释。
 * </p>
 * 通过配置的AnnotationResolver实例 从MethodInvocation获得注释。
 * 除非被重写，否则默认的AnnotationResolver就是一个可以获得注解的实例。
 * </p>
 *
 * @since 0.9
 */
public abstract class AnnotationMethodInterceptor extends MethodInterceptorSupport {

    private AnnotationHandler handler;

    /**
     * The resolver to use to find annotations on intercepted methods.
     * <p/>
     * 用于在拦截的方法上查找注释的解析器。
     * <p/>
     *
     * @since 1.1
     */
    private AnnotationResolver resolver;

    /**
     * Constructs an <code>AnnotationMethodInterceptor</code> with the
     * {@link AnnotationHandler AnnotationHandler} that will be used to process annotations of a
     * corresponding type.
     * <p/>
     * 构造AnnotationMethodInterceptor，参数是用于处理相应类型的注解的AnnotationHandler
     * <p/>
     *
     * @param handler the handler to delegate to for processing the annotation.
     */
    public AnnotationMethodInterceptor(AnnotationHandler handler) {
        this(handler, new DefaultAnnotationResolver());
    }

    /**
     * Constructs an <code>AnnotationMethodInterceptor</code> with the
     * {@link AnnotationHandler AnnotationHandler} that will be used to process annotations of a
     * corresponding type, using the specified {@code AnnotationResolver} to acquire annotations
     * at runtime.
     * <p/>
     * 构造函数，参数是 AnnotationHandler 和 AnnotationResolver。
     * AnnotationHandler：处理相应类型的注解。
     * AnnotationResolver：运行时获取注解。
     * <p/>
     *
     * @param handler  the handler to use to process any discovered annotation
     * @param resolver the resolver to use to locate/acquire the annotation
     * @since 1.1
     */
    public AnnotationMethodInterceptor(AnnotationHandler handler, AnnotationResolver resolver) {
        if (handler == null) {
            throw new IllegalArgumentException("AnnotationHandler argument cannot be null.");
        }
        setHandler(handler);
        setResolver(resolver != null ? resolver : new DefaultAnnotationResolver());
    }

    /**
     * Returns the {@code AnnotationHandler} used to perform authorization behavior based on
     * an annotation discovered at runtime.
     * <p/>
     * 返回用于 根据运行时发现的注解 执行授权行为的 AnnotationHandler
     * <p/>
     *
     * @return the {@code AnnotationHandler} used to perform authorization behavior based on
     *         an annotation discovered at runtime.
     */
    public AnnotationHandler getHandler() {
        return handler;
    }

    /**
     * Sets the {@code AnnotationHandler} used to perform authorization behavior based on
     * an annotation discovered at runtime.
     * <p/>
     * 设置用于 基于运行时发现的注解 执行授权行为的 AnnotationHandler。
     * <p/>
     * @param handler the {@code AnnotationHandler} used to perform authorization behavior based on
     *                an annotation discovered at runtime.
     */
    public void setHandler(AnnotationHandler handler) {
        this.handler = handler;
    }

    /**
     * Returns the {@code AnnotationResolver} to use to acquire annotations from intercepted
     * methods at runtime.  The annotation is then used by the {@link #getHandler handler} to
     * perform authorization logic.
     * <p/>
     * 设置用于在运行时从拦截的方法中获取注解的AnnotationResolver。
     * 然后，AnnotationHandler使用注解执行授权逻辑。
     * <p/>
     * @return the {@code AnnotationResolver} to use to acquire annotations from intercepted
     *         methods at runtime.
     * @since 1.1
     */
    public AnnotationResolver getResolver() {
        return resolver;
    }

    /**
     * Returns the {@code AnnotationResolver} to use to acquire annotations from intercepted
     * methods at runtime.  The annotation is then used by the {@link #getHandler handler} to
     * perform authorization logic.
     * <p/>
     * 设置用于在运行时从拦截的方法中获取注解的AnnotationResolver。
     * <p/>
     *
     * @param resolver the {@code AnnotationResolver} to use to acquire annotations from intercepted
     *                 methods at runtime.
     * @since 1.1
     */
    public void setResolver(AnnotationResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Returns <code>true</code> if this interceptor supports, that is, should inspect, the specified
     * <code>MethodInvocation</code>, <code>false</code> otherwise.
     * <p/>
     * The default implementation simply does the following:
     * <p/>
     * <code>return {@link #getAnnotation(MethodInvocation) getAnnotation(mi)} != null</code>
     * <p/>
     * 如果拦截器支持，即应该检查指定的MethodInvocation，则返回true，否则返回false
     * <p/>
     * @param mi the <code>MethodInvocation</code> for the method being invoked.
     * @return <code>true</code> if this interceptor supports, that is, should inspect, the specified
     *         <code>MethodInvocation</code>, <code>false</code> otherwise.
     */
    public boolean supports(MethodInvocation mi) {
        return getAnnotation(mi) != null;
    }

    /**
     * Returns the Annotation that this interceptor will process for the specified method invocation.
     * <p/>
     * 返回此拦截器将要 为指定的方法调用 处理的Annotation。
     * <p/>
     * The default implementation acquires the annotation using an annotation
     * {@link #getResolver resolver} using the internal annotation {@link #getHandler handler}'s
     * {@link org.apache.shiro.aop.AnnotationHandler#getAnnotationClass() annotationClass}.
     *
     * @param mi the MethodInvocation wrapping the Method from which the Annotation will be acquired.
     * @return the Annotation that this interceptor will process for the specified method invocation.
     */
    protected Annotation getAnnotation(MethodInvocation mi) {
        return getResolver().getAnnotation(mi, getHandler().getAnnotationClass());
    }
}
