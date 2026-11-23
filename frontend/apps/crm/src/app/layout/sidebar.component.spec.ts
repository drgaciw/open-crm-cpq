import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SidebarComponent } from './sidebar.component';
import { NAV_ITEMS } from './nav-items';

describe('SidebarComponent', () => {
  let fixture: ComponentFixture<SidebarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
  });

  it('uses Title Case labels for Pricing Customers, Rule Sets, Pricing Simulator', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Pricing Customers');
    expect(text).toContain('Rule Sets');
    expect(text).toContain('Pricing Simulator');
    expect(text).not.toContain('Pricing customers');
    expect(text).not.toContain('Rule sets');
  });

  it('keeps Bundles path and label (P1)', () => {
    const el = fixture.nativeElement as HTMLElement;
    const bundles = el.querySelector(
      'a[href="/product-bundles"]',
    ) as HTMLAnchorElement | null;
    expect(bundles).toBeTruthy();
    expect(bundles?.textContent).toContain('Bundles');
  });

  it('groups CRM and CPQ sections without adding routes', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('CRM');
    expect(el.textContent).toContain('CPQ');
    const links = Array.from(el.querySelectorAll('a[href]')).map((a) =>
      a.getAttribute('href'),
    );
    expect(links).toEqual(NAV_ITEMS.map((item) => item.route));
    expect(links).toEqual([
      '/dashboard',
      '/organizations',
      '/contacts',
      '/leads',
      '/deals',
      '/catalog',
      '/quotes',
      '/approvals',
      '/price-books',
      '/pricing-customers',
      '/rule-sets',
      '/product-bundles',
      '/pricing/simulator',
    ]);
  });

  it('renders labels and icons from NAV_ITEMS in order', () => {
    const el = fixture.nativeElement as HTMLElement;
    const anchors = Array.from(el.querySelectorAll('a[href]'));
    expect(anchors.length).toBe(NAV_ITEMS.length);
    NAV_ITEMS.forEach((item, index) => {
      const anchor = anchors[index];
      expect(anchor.querySelector('mat-icon')?.textContent?.trim()).toBe(item.icon);
      expect(anchor.textContent).toContain(item.label);
    });
  });
});
