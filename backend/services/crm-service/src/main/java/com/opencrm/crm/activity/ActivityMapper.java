package com.opencrm.crm.activity;

/**
 * Maps an activity entity to the API read model. Entities stay off the controller.
 */
public final class ActivityMapper {

    private ActivityMapper() {
    }

    public static ActivityDto toDto(Activity activity) {
        return ActivityDto.builder()
                .id(activity.getId())
                .uuid(activity.getUuid())
                .type(activity.getType())
                .subject(activity.getSubject())
                .body(activity.getBody())
                .organizationId(activity.getOrganizationId())
                .contactId(activity.getContactId())
                .dealId(activity.getDealId())
                .authorId(activity.getAuthorId())
                .authorKind(activity.getAuthorKind())
                .dueAt(activity.getDueAt())
                .completedAt(activity.getCompletedAt())
                .occurredAt(activity.getOccurredAt())
                .emailThreadRef(activity.getEmailThreadRef())
                .calendarEventRef(activity.getCalendarEventRef())
                .createdAt(activity.getCreatedAt())
                .updatedAt(activity.getUpdatedAt())
                .build();
    }
}
