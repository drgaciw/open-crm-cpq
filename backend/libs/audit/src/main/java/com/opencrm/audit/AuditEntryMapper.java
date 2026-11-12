package com.opencrm.audit;

import com.opencrm.audit.dto.AuditEntryDto;
import org.mapstruct.Mapper;

/**
 * Entity -&gt; DTO mapping for {@link AuditEntry} (ADR-006: the entity itself is never returned from a REST
 * boundary). Uses MapStruct's default (framework-agnostic) component model rather than {@code componentModel =
 * "spring"}: this library is wired into a consuming service through {@link AuditAutoConfiguration}, not component
 * scanning (see that class's Javadoc), so the generated implementation is looked up with
 * {@code org.mapstruct.factory.Mappers#getMapper} and registered there as a bean.
 */
@Mapper
public interface AuditEntryMapper {

    AuditEntryDto toDto(AuditEntry entry);
}
