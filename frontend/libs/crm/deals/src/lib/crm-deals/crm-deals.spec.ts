import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CrmDeals } from './crm-deals';

describe('CrmDeals', () => {
  let component: CrmDeals;
  let fixture: ComponentFixture<CrmDeals>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CrmDeals],
    }).compileComponents();

    fixture = TestBed.createComponent(CrmDeals);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
