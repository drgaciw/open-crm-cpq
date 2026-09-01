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

describe('PageHeaderComponent', () => {
  async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    // afterNextRender runs before paint; flush microtasks + another CD
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
  }

  it('should create', async () => {
    await TestBed.configureTestingModule({
      imports: [HostWithoutSearchComponent],
    }).compileComponents();

    const fixture = TestBed.createComponent(HostWithoutSearchComponent);
    await settle(fixture);
    expect(fixture.nativeElement.querySelector('app-page-header')).toBeTruthy();
  });

  it('should hide search slot when no [search] projection is provided', async () => {
    await TestBed.configureTestingModule({
      imports: [HostWithoutSearchComponent],
    }).compileComponents();

    const fixture = TestBed.createComponent(HostWithoutSearchComponent);
    await settle(fixture);

    const search = fixture.nativeElement.querySelector('.page-header-search') as HTMLElement;
    expect(search).toBeTruthy();
    expect(search.hidden).toBe(true);
  });

  it('should show search slot when [search] projection is provided', async () => {
    await TestBed.configureTestingModule({
      imports: [HostWithSearchComponent],
    }).compileComponents();

    const fixture = TestBed.createComponent(HostWithSearchComponent);
    await settle(fixture);

    const search = fixture.nativeElement.querySelector('.page-header-search') as HTMLElement;
    expect(search).toBeTruthy();
    expect(search.hidden).toBe(false);
    expect(search.querySelector('input[type="search"]')).toBeTruthy();
  });
});
