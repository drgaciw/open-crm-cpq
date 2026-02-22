import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CrmLeads } from './crm-leads';

describe('CrmLeads', () => {
  let component: CrmLeads;
  let fixture: ComponentFixture<CrmLeads>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CrmLeads],
    }).compileComponents();

    fixture = TestBed.createComponent(CrmLeads);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
