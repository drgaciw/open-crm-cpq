import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { RuleSetCreateDialogComponent } from './rule-set-create-dialog.component';
import { RuleSetService } from '@frontend/cpq-pricing-data-access';
import { MOCK_RULE_SETS } from '../rule-sets.mock';

describe('RuleSetCreateDialogComponent', () => {
  let fixture: ComponentFixture<RuleSetCreateDialogComponent>;
  let component: RuleSetCreateDialogComponent;
  let createSpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    createSpy = vi.fn().mockReturnValue(of(MOCK_RULE_SETS[0]));
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [RuleSetCreateDialogComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: RuleSetService,
          useValue: { create: createSpy },
        },
        {
          provide: MatDialogRef,
          useValue: { close: dialogClose },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RuleSetCreateDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requires name', () => {
    component.onSubmit();
    expect(createSpy).not.toHaveBeenCalled();
    expect(component.form.invalid).toBe(true);
  });

  it('creates via RuleSetService.create and closes with result', () => {
    component.form.patchValue({
      name: 'New Set',
      description: 'Desc',
      engineType: 'DROOLS',
      effectiveDate: '2026-04-01',
    });
    component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      name: 'New Set',
      description: 'Desc',
      engineType: 'DROOLS',
      effectiveDate: '2026-04-01',
    });
    expect(dialogClose).toHaveBeenCalledWith(MOCK_RULE_SETS[0]);
    expect(component.saving()).toBe(false);
  });

  it('surfaces create error in dialog banner', () => {
    createSpy.mockReturnValueOnce(
      throwError(() => ({ error: { message: 'Duplicate name' } })),
    );
    component.form.patchValue({ name: 'Dup' });
    component.onSubmit();
    fixture.detectChanges();

    expect(component.error()).toBe('Duplicate name');
    expect(dialogClose).not.toHaveBeenCalled();
    expect(
      (fixture.nativeElement as HTMLElement).textContent,
    ).toContain('Duplicate name');
  });
});
