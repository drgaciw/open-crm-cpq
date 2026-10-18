package com.opencrm.contract.documents.apply;

import com.opencrm.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** An apply refusal raised inside CAM after its audit row has committed. */
public class DocumentApplyException extends BusinessException {

    public DocumentApplyException(String message, HttpStatus status, String errorCode) {
        super(message, status, errorCode);
    }
}
