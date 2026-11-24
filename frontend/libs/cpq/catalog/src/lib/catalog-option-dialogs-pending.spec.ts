import { type Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { type ProductOptionGroupDto } from '@frontend/shared-models';
import { Subject } from 'rxjs';
import { OptionCreateDialogComponent } from './option-create-dialog/option-create-dialog.component';
import { OptionGroupCreateDialogComponent } from './option-group-create-dialog/option-group-create-dialog.component';
import { ProductOptionService } from './product-option.service';

type CatalogOptionDialog = OptionGroupCreateDialogComponent | OptionCreateDialogComponent;

async function setup(editor: Type<CatalogOptionDialog>) {
  const reply = new Subject<ProductOptionGroupDto>();
  const submit = vi.fn(() => reply.asObservable());
  const dialogRef = { close: vi.fn(), disableClose: false };
  await TestBed.configureTestingModule({
    imports: [editor],
    providers: [
      provideNoopAnimations(),
      { provide: MAT_DIALOG_DATA, useValue: { groupId: 7, groupName: 'Scoped group', productId: 42 } },
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: ProductOptionService, useValue: { createGroup: submit, addOption: submit } },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(editor);
  const component = fixture.componentInstance;
  if (component instanceof OptionGroupCreateDialogComponent) {
    component.form.controls.selectionType.setValue('SINGLE');
  }
  fixture.detectChanges();
  return { fixture, component, reply, submit, dialogRef };
}

describe.each([
  { editor: OptionGroupCreateDialogComponent, action: 'Create', progress: 'Creating option group' },
  { editor: OptionCreateDialogComponent, action: 'Add', progress: 'Adding option' },
])('catalog option dialog $action', ({ editor, action, progress }) => {
  it('blocks whitespace-only names before posting', async () => {
    const { component, submit } = await setup(editor);
    component.form.controls.name.setValue('   ');

    component.onSubmit();

    expect(component.form.controls.name.hasError('required')).toBe(true);
    expect(submit).not.toHaveBeenCalled();
  });

  it('names the pending action and restores the draft after a failed request', async () => {
    const { fixture, component, reply, submit, dialogRef } = await setup(editor);
    component.form.controls.name.setValue('Scoped draft');

    component.onSubmit();
    component.onSubmit();
    component.onCancel();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const button = host.querySelector<HTMLButtonElement>(`button[aria-label="${action}"]`);
    expect(button?.disabled).toBe(true);
    expect(button?.querySelector('[role="progressbar"]')?.getAttribute('aria-label')).toBe(progress);
    expect(component.form.disabled).toBe(true);
    expect(dialogRef.disableClose).toBe(true);
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(submit).toHaveBeenCalledTimes(1);

    reply.error(new Error('Controlled failure'));
    fixture.detectChanges();
    expect(component.form.enabled).toBe(true);
    expect(component.form.controls.name.value).toBe('Scoped draft');
    expect(dialogRef.disableClose).toBe(false);
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
