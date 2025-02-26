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

import org.apache.shiro.lang.ShiroException;

/**
 * Shiro container-agnostic interface that indicates that this object requires initialization.
 * <p/>
 * 与 Shiro 容器无关的接口，指示该对象需要初始化。
 * @since 0.2
 */
public interface Initializable {

    /**
     * Initializes this object.
     *
     * @throws ShiroException
     *          if an exception occurs during initialization.
     */
    void init() throws ShiroException;

}
