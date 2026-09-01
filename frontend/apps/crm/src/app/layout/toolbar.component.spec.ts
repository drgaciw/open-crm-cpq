import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToolbarComponent } from './toolbar.component';

describe('ToolbarComponent', () => {
  let fixture: ComponentFixture<ToolbarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ToolbarComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ToolbarComponent);
    fixture.detectChanges();
  });

  it('exposes menu toggle and does not render inert Notifications/Account', () => {
    const el = fixture.nativeElement as HTMLElement;
    const toggle = el.querySelector(
      'button[aria-label="Toggle menu"]',
    ) as HTMLButtonElement | null;
    expect(toggle).toBeTruthy();
    expect(el.querySelector('button[aria-label="Notifications"]')).toBeNull();
    expect(el.querySelector('button[aria-label="Account"]')).toBeNull();
  });
});
