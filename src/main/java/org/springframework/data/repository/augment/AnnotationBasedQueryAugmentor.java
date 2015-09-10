/*
 * Copyright 2013-2015 the original author or authors.
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
package org.springframework.data.repository.augment;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.repository.augment.QueryContext.QueryMode;
import org.springframework.data.repository.core.EntityMetadata;

/**
 * Base implementation of {@link QueryAugmentor} to lookup an annotation on the repository method invoked or at the
 * repository interface. It caches the lookups to avoid repeated reflection calls and hands the annotation found into
 * {@link #prepareQuery(QueryContext, Annotation)} and {@link #prepareUpdate(UpdateContext, Annotation)} methods. Opts
 * out of augmentation in case the annotation cannot be found on the method invoked or in the type.
 * 
 * @since 1.11
 * @author Oliver Gierke
 * @param T the annotation type
 * @param Q the {@link QueryContext} type to be used for store specific queries.
 * @param N the {@link QueryContext} type to be used to native queries.
 * @param U the {@link UpdateContext} type to be used.
 */
public abstract class AnnotationBasedQueryAugmentor<T extends Annotation, Q extends QueryContext<?>, N extends QueryContext<?>, U extends UpdateContext<?>>
		implements QueryAugmentor<Q, N, U> {

	private final Map<Method, T> cache = new HashMap<Method, T>();
	private final Class<T> annotationType;

	/**
	 * Creates a new {@link AnnotationBasedQueryAugmentor}.
	 */
	@SuppressWarnings("unchecked")
	public AnnotationBasedQueryAugmentor() {
		this.annotationType = (Class<T>) GenericTypeResolver.resolveTypeArguments(getClass(),
				AnnotationBasedQueryAugmentor.class)[0];
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.core.support.QueryAugmentor#supports(org.springframework.data.repository.core.support.MethodMetadata, org.springframework.data.repository.core.support.QueryContext.Mode, org.springframework.data.repository.core.EntityMetadata)
	 */
	public boolean supports(MethodMetadata method, QueryMode queryMode, EntityMetadata<?> entityMetadata) {

		if (cache.containsKey(method)) {
			return cache.get(method) == null;
		}

		return findAndCacheAnnotation(method) != null;
	}

	/**
	 * Finds the annotation using the given {@link MethodMetadata} and caches it if found.
	 * 
	 * @param metadata must not be {@literal null}.
	 * @return
	 */
	private T findAndCacheAnnotation(MethodMetadata metadata) {

		Method method = metadata.getMethod();
		T expression = AnnotationUtils.findAnnotation(method, annotationType);

		if (expression != null) {
			cache.put(method, expression);
			return expression;
		}

		for (Class<?> type : metadata.getInvocationTargetType()) {

			expression = findAndCache(type, method);

			if (expression != null) {
				return expression;
			}
		}

		return findAndCache(method.getDeclaringClass(), method);
	}

	/**
	 * Tries to find the annotation on the given type and caches it if found.
	 * 
	 * @param type must not be {@literal null}.
	 * @param method must not be {@literal null}.
	 * @return
	 */
	private T findAndCache(Class<?> type, Method method) {

		T annotation = AnnotationUtils.findAnnotation(type, annotationType);

		if (annotation != null) {
			cache.put(method, annotation);
			return annotation;
		}

		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.augment.QueryAugmentor#augmentNativeQuery(org.springframework.data.repository.augment.QueryContext, org.springframework.data.repository.augment.MethodMetadata)
	 */
	public final N augmentNativeQuery(N context, MethodMetadata metadata) {

		Method method = metadata.getMethod();

		if (cache.containsKey(method)) {
			return prepareNativeQuery(context, cache.get(method));
		}

		T expression = findAndCacheAnnotation(metadata);
		return expression == null ? context : prepareNativeQuery(context, expression);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.augment.QueryAugmentor#augmentQuery(org.springframework.data.repository.augment.QueryContext, org.springframework.data.repository.augment.MethodMetadata)
	 */
	public final Q augmentQuery(Q context, MethodMetadata metadata) {

		Method method = metadata.getMethod();

		if (cache.containsKey(method)) {
			return prepareQuery(context, cache.get(method));
		}

		T expression = findAndCacheAnnotation(metadata);
		return expression == null ? context : prepareQuery(context, expression);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.augment.QueryAugmentor#augmentUpdate(org.springframework.data.repository.augment.UpdateContext, org.springframework.data.repository.augment.MethodMetadata)
	 */
	public final U augmentUpdate(U update, MethodMetadata metadata) {

		Method method = metadata.getMethod();

		if (cache.containsKey(method)) {
			return prepareUpdate(update, cache.get(method));
		}

		T expression = findAndCacheAnnotation(metadata);
		return expression == null ? update : prepareUpdate(update, cache.get(method));
	}

	protected N prepareNativeQuery(N context, T annotation) {
		return context;
	}

	/**
	 * Prepare the query contained in the given {@link QueryContext} using the given annotation. Default implementation
	 * returns the context as is.
	 * 
	 * @param context will never be {@literal null}.
	 * @param annotation will never be {@literal null}.
	 * @return
	 */
	protected Q prepareQuery(Q context, T annotation) {
		return context;
	}

	/**
	 * Prepare the update contained in the given {@link UpdateContext} using the given annotation. Default implementation
	 * returns the context as is.
	 * 
	 * @param context will never be {@literal null}.
	 * @param annotation will never be {@literal null}.
	 * @return
	 */
	protected U prepareUpdate(U context, T annotation) {
		return context;
	}
}