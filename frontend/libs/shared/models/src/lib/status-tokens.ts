/**
 * CRM status/stage color values as CSS custom-property references (UI-CRM-SLDS).
 * Defined on `:root` in `frontend/apps/crm/src/styles.scss`.
 * Use these (or maps built from them) instead of hardcoded hex in feature UI.
 */

export interface CrmStatusTone {
  /** Foreground / border ink */
  ink: string;
  /** Soft fill */
  container: string;
}

const tone = (ink: string, container: string): CrmStatusTone => ({ ink, container });

/** Semantic tones shared by lead / deal / product / quote badges. */
export const CRM_STATUS_TONES = {
  info: tone('var(--crm-color-info)', 'var(--crm-color-info-container)'),
  infoStrong: tone('var(--crm-color-brand)', 'var(--crm-color-info-container)'),
  success: tone('var(--crm-color-success)', 'var(--crm-color-success-container)'),
  successStrong: tone('var(--crm-color-success-strong, #2e7d32)', 'var(--crm-color-success-strong-container, #c8e6c9)'),
  warning: tone('var(--crm-color-warning)', 'var(--crm-color-warning-container)'),
  danger: tone('var(--crm-color-danger)', 'var(--crm-color-danger-container)'),
  accent: tone('var(--crm-color-accent)', 'var(--crm-color-accent-container)'),
  teal: tone('var(--crm-color-teal)', 'var(--crm-color-teal-container)'),
  muted: tone('var(--crm-color-muted)', 'var(--crm-color-muted-container)'),
  neutral: tone('var(--crm-color-neutral)', 'var(--crm-color-neutral-container)'),
} as const;

export type CrmStatusToneName = keyof typeof CRM_STATUS_TONES;
