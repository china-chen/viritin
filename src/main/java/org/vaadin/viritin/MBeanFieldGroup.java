/*
 * Copyright 2014 Matti Tahvonen.
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
package org.vaadin.viritin;

import com.vaadin.data.Property;
import com.vaadin.data.Validator;
import com.vaadin.data.fieldgroup.BeanFieldGroup;
import com.vaadin.data.validator.BeanValidator;
import com.vaadin.event.*;
import com.vaadin.event.FieldEvents.TextChangeNotifier;
import com.vaadin.server.AbstractErrorMessage;
import com.vaadin.server.CompositeErrorMessage;
import com.vaadin.server.ErrorMessage;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.AbstractField;
import com.vaadin.ui.Field;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;

import javax.validation.constraints.NotNull;
import org.vaadin.viritin.fields.MPasswordField;

import org.vaadin.viritin.fields.MTextField;

/**
 * Enhanced version of basic BeanFieldGroup in Vaadin. Supports "eager
 * validation" and some enhancements to bean validation support.
 *
 * @param <T> the type of the bean wrapped by this group
 */
public class MBeanFieldGroup<T> extends BeanFieldGroup<T> implements
        Property.ValueChangeListener, FieldEvents.TextChangeListener {

    protected final Class nonHiddenBeanType;
    private Set<ConstraintViolation<T>> constraintViolations;

    /**
     * Configures fields for some better defaults, like property fields
     * annotated with NotNull to be "required" (kind of a special validator in
     * Vaadin)
     */
    public void configureMaddonDefaults() {
        for (Object property : getBoundPropertyIds()) {
            final Field<?> field = getField(property);

            // Make @NotNull annotated fields "required"
            try {
                java.lang.reflect.Field declaredField = findDeclaredField(
                        property, nonHiddenBeanType);
                final NotNull notNullAnnotation = declaredField.getAnnotation(
                        NotNull.class);
                if (notNullAnnotation != null) {
                    field.setRequired(true);
                    if (notNullAnnotation.message() != null) {
                        getField(property).setRequiredError(notNullAnnotation.
                                message());
                    }
                }
            } catch (NoSuchFieldException ex) {
                Logger.getLogger(MBeanFieldGroup.class.getName()).
                        log(Level.FINE, null, ex);
            } catch (SecurityException ex) {
                Logger.getLogger(MBeanFieldGroup.class.getName()).
                        log(Level.SEVERE, null, ex);
            }
        }
    }

    protected java.lang.reflect.Field findDeclaredField(Object property,
            Class clazz) throws NoSuchFieldException, SecurityException {
        try {
            java.lang.reflect.Field declaredField = clazz.
                    getDeclaredField(property.
                            toString());
            return declaredField;
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() == null) {
                throw e;
            } else {
                return findDeclaredField(property, clazz.getSuperclass());
            }
        }
    }

    private final Set<String> fieldsWithInitiallyDisabledValidation = new HashSet<String>();

    public Set<String> getFieldsWithInitiallyDisabledValidation() {
        return Collections.
                unmodifiableSet(fieldsWithInitiallyDisabledValidation);
    }

    /**
     * This method hides validation errors on a required fields until the field
     * has been changed for the first time. Does pretty much the same as old
     * Vaadin Form did with its validationVisibleOnCommit, but eagerly per
     * field.
     * <p>
     * Fields that hide validation errors this way are available in
     * getFieldsWithIntiallyDisabledValidation() so they can be emphasized in
     * UI.
     */
    public void hideInitialEmpyFieldValidationErrors() {
        fieldsWithInitiallyDisabledValidation.clear();
        for (Field f : getFields()) {
            if (f instanceof AbstractField) {
                final AbstractField abstractField = (AbstractField) f;
                if (abstractField.getErrorMessage() != null && abstractField.
                        isRequired() && abstractField.
                        isEmpty() && abstractField.isValidationVisible()) {
                    final String propertyId = getPropertyId(abstractField).
                            toString();
                    abstractField.setValidationVisible(false);
                    fieldsWithInitiallyDisabledValidation.add(propertyId);
                }
            }
        }
    }

    /**
     * @return constraint violations found in last top level JSR303 validation.
     */
    public Set<ConstraintViolation<T>> getConstraintViolations() {
        return constraintViolations;
    }
    
    public Collection<String> getBeanLevelValidationErrors() {
        Collection<String> errors = new ArrayList<String>();
        if(getConstraintViolations() != null) {
            for (ConstraintViolation<T> constraintViolation : getConstraintViolations()) {
                errors.add(constraintViolation.getMessage());
            }
        }
        for (Map.Entry<ErrorMessage, AbstractComponent> e : mValidationErrors.
                entrySet()) {
            AbstractComponent value = e.getValue();
            if(value == null) {
                errors.add(e.getKey().getFormattedHtmlMessage());
            }
        }
        return errors;
    }

    // For JSR303 validation at class level
    private static ValidatorFactory factory;
    private transient javax.validation.Validator javaxBeanValidator;

    protected boolean jsr303ValidateBean(T bean) {
        if (factory == null) {
            factory = Validation.buildDefaultValidatorFactory();
        }
        if (javaxBeanValidator == null) {
            javaxBeanValidator = factory.getValidator();
        }
        Set<ConstraintViolation<T>> constraintViolations = javaxBeanValidator.
                validate(bean);
        if (constraintViolations.isEmpty()) {
            return true;
        }
        this.constraintViolations = constraintViolations;
        return false;
    }

    public interface FieldGroupListener<T> {

        public void onFieldGroupChange(MBeanFieldGroup<T> beanFieldGroup);

    }

    /**
     * EXPERIMENTAL: The cross field validation support is still experimental
     * and its API is likely to change.
     *
     * A validator executed against the edited bean. Developer can do any
     * validation within the validate method, but typically this type of
     * validation are used for e.g. cross field validation which is not possible
     * with BeanValidation support in Vaadin.
     *
     * @param <T> the bean type to be validated.
     *
     */
    public interface MValidator<T> extends Serializable {

        /**
         *
         * @param value the bean to be validated
         * @throws Validator.InvalidValueException
         */
        public void validate(T value) throws Validator.InvalidValueException;

    }

    @Override
    public void valueChange(Property.ValueChangeEvent event) {
        if (event != null) {
            Property property = event.getProperty();
            if (property instanceof AbstractField) {
                AbstractField abstractField = (AbstractField) property;
                String propertyId = getPropertyId(abstractField).toString();
                boolean wasHiddenValidation = fieldsWithInitiallyDisabledValidation.
                        remove(propertyId);
                if (wasHiddenValidation) {
                    abstractField.setValidationVisible(true);
                }
            }
        }
        setBeanModified(true);
        if (listener != null) {
            listener.onFieldGroupChange(this);
        }
    }

    private LinkedHashMap<MValidator<T>, Collection<String>> mValidators = new LinkedHashMap<MValidator<T>, Collection<String>>();

    /**
     * EXPERIMENTAL: The cross field validation support is still experimental
     * and its API is likely to change.
     *
     * @param validator a validator that validates the whole bean making cross
     * field validation much simpler
     * @param properties the properties that this validator affects and on which
     * a possible error message is shown.
     * @return this FieldGroup
     */
    public MBeanFieldGroup<T> addValidator(MValidator<T> validator,
            String... properties) {
        mValidators.put(validator, Arrays.asList(properties));
        return this;
    }

    public MBeanFieldGroup<T> removeValidator(MValidator<T> validator) {
        mValidators.remove(validator);
        return this;
    }

    /**
     * Removes all MValidators added the MFieldGroup
     *
     * @return the instance
     */
    public MBeanFieldGroup<T> clearValidators() {
        mValidators.clear();
        return this;
    }

    private Map<ErrorMessage, AbstractComponent> mValidationErrors = new HashMap<ErrorMessage, AbstractComponent>();

    private void clearMValidationErrors() {
        for (AbstractComponent value : mValidationErrors.values()) {
            if (value != null) {
                value.setComponentError(null);
            }
        }
        mValidationErrors.clear();
    }

    @Override
    public boolean isValid() {
        // clear all MValidation errors
        clearMValidationErrors();
        constraintViolations = null;

        // first check standard property level validators
        final boolean propertiesValid = super.isValid();
        if (propertiesValid) {
            // then crossfield(/bean level) validators 
            for (MValidator<T> v : mValidators.keySet()) {
                try {
                    v.validate(getItemDataSource().getBean());
                } catch (Validator.InvalidValueException e) {
                    Collection<String> properties = mValidators.get(v);
                    if (!properties.isEmpty()) {
                        for (String p : properties) {
                        final ErrorMessage em = AbstractErrorMessage.
                                getErrorMessageForException(e);
                            Field<?> field = getField(p);
                            if (field instanceof AbstractComponent) {
                                AbstractComponent abstractField = (AbstractComponent) field;
                                mValidationErrors.put(em, abstractField);
                                abstractField.setComponentError(em);
                            }
                        }
                    } else {
                        final ErrorMessage em = AbstractErrorMessage.
                                getErrorMessageForException(e);
                        mValidationErrors.put(em, null);
                    }
                    return false;
                }
            }
            return jsr303ValidateBean(getItemDataSource().getBean());
        }
        return false;
    }

    @Override
    public void textChange(FieldEvents.TextChangeEvent event) {
        valueChange(null);
    }

    private boolean beanModified = false;
    private FieldGroupListener<T> listener;

    public void setBeanModified(boolean beanModified) {
        this.beanModified = beanModified;
    }

    public boolean isBeanModified() {
        return beanModified;
    }

    @Override
    public boolean isModified() {
        return super.isModified();
    }

    public MBeanFieldGroup(Class beanType) {
        super(beanType);
        this.nonHiddenBeanType = beanType;
    }

    public MBeanFieldGroup<T> withEagerValidation() {
        return withEagerValidation(new FieldGroupListener() {
            @Override
            public void onFieldGroupChange(MBeanFieldGroup beanFieldGroup) {
            }
        });
    }

    /**
     * Makes all fields "immediate" to trigger eager validation
     *
     * @param listener a listener that will be notified when a field in the
     * group has been modified
     * @return the MBeanFieldGroup that can be used for further modifications or
     * e.g. commit if buffered
     */
    public MBeanFieldGroup<T> withEagerValidation(FieldGroupListener<T> listener) {
        this.listener = listener;
        for (Field<?> field : getFields()) {
            ((AbstractComponent) field).setImmediate(true);
            field.addValueChangeListener(this);
            if (field instanceof MTextField) {
                final MTextField abstractTextField = (MTextField) field;
                abstractTextField.setEagerValidation(true);
            }
            // TODO DRY, create interface eagervalidateable, or just push Vaadin
            // core team to get this done
            if (field instanceof MPasswordField) {
                final MPasswordField abstractPwField = (MPasswordField) field;
                abstractPwField.setEagerValidation(true);
            }
            if (field instanceof TextChangeNotifier) {
                final TextChangeNotifier abstractTextField = (TextChangeNotifier) field;
                abstractTextField.addTextChangeListener(this);
            }
        }
        return this;
    }

    /**
     * Removes all listeners from the bound fields and unbinds properties.
     */
    public void unbind() {
        // wrap in array list to avoid CME
        for (Field<?> field : new ArrayList<Field<?>>(getFields())) {
            field.removeValueChangeListener(this);
            if (field instanceof TextChangeNotifier) {
                final TextChangeNotifier abstractTextField = (TextChangeNotifier) field;
                abstractTextField.removeTextChangeListener(this);
            }

            unbind(field);
        }
        fieldsWithInitiallyDisabledValidation.clear();

    }

}
