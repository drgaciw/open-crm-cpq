import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PageHeaderComponent } from './page-header.component';

@Component({
  standalone: true,
  imports: [PageHeaderComponent],
  template: `
    <app-page-header title="Detail" subtitle="No search here">
      <div actions>
        <button type="button">Edit</button>
      </div>
    </app-page-header>
  `,
})
class HostWithoutSearchComponent {}

@Component({
  standalone: true,
  imports: [PageHeaderComponent],
  template: `
    <app-page-header title="List" subtitle="With search">
      <div actions>
        <button type="button">New</button>
      </div>
      <div search>
        <input type="search" placeholder="Search" />
      </div>
    </app-page-header>
  `,
})
class HostWithSearchComponent {}

@Component({
  standalone: true,
  imports: [PageHeaderComponent],
  template: `
    <app-page-header title="Acme" subtitle="Technology">
      <nav breadcrumb aria-label="Breadcrumb">
        <a href="/organizations">Accounts</a>
        <span> / </span>
        <span>Acme</span>
      </nav>
      <div actions>
        <button type="button">Edit</button>
      </div>
      <div highlights>
        <span class="hl">Phone</span>
      </div>
    </app-page-header>
  `,
})
class HostWithRecordSlotsComponent {}

describe('PageHeaderComponent', () => {
  async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    // afterNextRender runs before paint; flush microtasks + another CD
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
  }

  // Angular TestBed compile can exceed Vitest's default timeouts under load.
  describe('without search projection', () => {
    let fixture: ComponentFixture<HostWithoutSearchComponent>;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [HostWithoutSearchComponent],
      }).compileComponents();

      fixture = TestBed.createComponent(HostWithoutSearchComponent);
      await settle(fixture);
    }, 30000);

    it('should create', () => {
      expect(fixture.nativeElement.querySelector('app-page-header')).toBeTruthy();
    });

    it('should hide search slot when no [search] projection is provided', () => {
      const search = fixture.nativeElement.querySelector('.page-header-search') as HTMLElement;
      expect(search).toBeTruthy();
      expect(search.hidden).toBe(true);
    });
  });

  describe('with search projection', () => {
    let fixture: ComponentFixture<HostWithSearchComponent>;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [HostWithSearchComponent],
      }).compileComponents();

      fixture = TestBed.createComponent(HostWithSearchComponent);
      await settle(fixture);
    }, 30000);

    it('should show search slot when [search] projection is provided', () => {
      const search = fixture.nativeElement.querySelector('.page-header-search') as HTMLElement;
      expect(search).toBeTruthy();
      expect(search.hidden).toBe(false);
      expect(search.querySelector('input[type="search"]')).toBeTruthy();
    });
  });

  describe('with record breadcrumb and highlights', () => {
    let fixture: ComponentFixture<HostWithRecordSlotsComponent>;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [HostWithRecordSlotsComponent],
      }).compileComponents();

      fixture = TestBed.createComponent(HostWithRecordSlotsComponent);
      await settle(fixture);
    }, 30000);

    it('shows breadcrumb and highlights when projected', () => {
      const el = fixture.nativeElement as HTMLElement;
      const crumb = el.querySelector('.page-header-breadcrumb') as HTMLElement;
      const highlights = el.querySelector('.page-header-highlights') as HTMLElement;
      expect(crumb.hidden).toBe(false);
      expect(crumb.querySelector('nav[aria-label="Breadcrumb"]')).toBeTruthy();
      expect(highlights.hidden).toBe(false);
      expect(highlights.textContent).toContain('Phone');
      expect(el.querySelector('.page-header--record')).toBeTruthy();
    });

    it('keeps list headers working without those slots', async () => {
      TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [HostWithoutSearchComponent],
      }).compileComponents();
      const listFixture = TestBed.createComponent(HostWithoutSearchComponent);
      await settle(listFixture);
      const el = listFixture.nativeElement as HTMLElement;
      expect((el.querySelector('.page-header-breadcrumb') as HTMLElement).hidden).toBe(true);
      expect((el.querySelector('.page-header-highlights') as HTMLElement).hidden).toBe(true);
    });
  });
});
