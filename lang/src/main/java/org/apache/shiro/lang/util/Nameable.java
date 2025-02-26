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
package org.apache.shiro.lang.util;


/**
 * Interface implemented by components that can be named, such as via configuration, and wish to have that name
 * set once it has been configured.
 * <p/>
 * 接口，由可以命名的组件实现，例如通过配置，并且希望在配置后设置该名称。
 * <p/>
 *
 * @since 0.9
 */
public interface Nameable {

    /**
     * Sets the (preferably application unique) name for this component.
     * <p/>
     * 为该组件设置(最好是应用程序中唯一的)名称。
     * <p/>
     * @param name the preferably application unique name for this component.
     */
    void setName(String name);
}
