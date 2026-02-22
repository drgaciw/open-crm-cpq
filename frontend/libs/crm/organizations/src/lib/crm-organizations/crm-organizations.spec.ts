import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CrmOrganizations } from './crm-organizations';

describe('CrmOrganizations', () => {
  let component: CrmOrganizations;
  let fixture: ComponentFixture<CrmOrganizations>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CrmOrganizations],
    }).compileComponents();

    fixture = TestBed.createComponent(CrmOrganizations);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
