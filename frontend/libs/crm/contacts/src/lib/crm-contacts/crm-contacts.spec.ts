import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CrmContacts } from './crm-contacts';

describe('CrmContacts', () => {
  let component: CrmContacts;
  let fixture: ComponentFixture<CrmContacts>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CrmContacts],
    }).compileComponents();

    fixture = TestBed.createComponent(CrmContacts);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
