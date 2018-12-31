/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.jvm.ReflectJvmMapping;

import org.springframework.lang.Nullable;

/**
 * {@link ParameterNameDiscoverer} implementation which uses Kotlin's reflection facilities
 * for introspecting parameter names.
 *
 * Compared to {@link StandardReflectionParameterNameDiscoverer}, it allows in addition to
 * determine interface parameter names without requiring Java 8 -parameters compiler flag.
 *
 * @author Sebastien Deleuze
 * @since 5.0
 */
public class KotlinReflectionParameterNameDiscoverer implements ParameterNameDiscoverer {

	@Override
	@Nullable
	public String[] getParameterNames(Method method) {
		if (!KotlinDetector.isKotlinType(method.getDeclaringClass())) {
			return null;
		}

		try {
			KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
			return (function != null ? getParameterNames(function.getParameters()) : null);
		}
		catch (UnsupportedOperationException ex) {
			return null;
		}
	}

	@Override
	@Nullable
	public String[] getParameterNames(Constructor<?> ctor) {
		if (!KotlinDetector.isKotlinType(ctor.getDeclaringClass())) {
			return null;
		}

		try {
			KFunction<?> function = ReflectJvmMapping.getKotlinFunction(ctor);
			return (function != null ? getParameterNames(function.getParameters()) : null);
		}
		catch (UnsupportedOperationException ex) {
			return null;
		}
	}

	@Nullable
	private String[] getParameterNames(List<KParameter> parameters) {
		List<KParameter> filteredParameters = parameters
				.stream()
				.filter(p -> KParameter.Kind.VALUE.equals(p.getKind()))
				.collect(Collectors.toList());
		String[] parameterNames = new String[filteredParameters.size()];
		for (int i = 0; i < filteredParameters.size(); i++) {
			String name = filteredParameters.get(i).getName();
			if (name == null) {
				return null;
			}
			parameterNames[i] = name;
		}
		return parameterNames;
	}

}
