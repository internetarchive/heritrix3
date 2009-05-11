package org.archive.spring;

import org.springframework.validation.Validator;

public interface HasValidator {
    Validator getValidator();
}
