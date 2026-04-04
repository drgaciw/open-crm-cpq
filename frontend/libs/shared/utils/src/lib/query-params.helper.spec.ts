import { HttpParams } from '@angular/common/http';
import { toHttpParams } from './query-params.helper';
import { PageRequest } from '@frontend/shared-models';

describe('toHttpParams', () => {
  it('should return empty HttpParams when no arguments are provided', () => {
    const params = toHttpParams();
    expect(params.keys().length).toBe(0);
  });

  it('should set page correctly when provided in PageRequest', () => {
    const pageRequest: PageRequest = { page: 2 };
    const params = toHttpParams(pageRequest);
    expect(params.get('page')).toBe('2');
  });

  it('should set page to 0 correctly when provided in PageRequest', () => {
    const pageRequest: PageRequest = { page: 0 };
    const params = toHttpParams(pageRequest);
    expect(params.get('page')).toBe('0');
  });

  it('should set size correctly when provided in PageRequest', () => {
    const pageRequest: PageRequest = { size: 10 };
    const params = toHttpParams(pageRequest);
    expect(params.get('size')).toBe('10');
  });

  it('should set sort correctly when provided in PageRequest', () => {
    const pageRequest: PageRequest = { sort: 'name,asc' };
    const params = toHttpParams(pageRequest);
    expect(params.get('sort')).toBe('name,asc');
  });

  it('should set all PageRequest parameters correctly', () => {
    const pageRequest: PageRequest = { page: 1, size: 20, sort: 'createdAt,desc' };
    const params = toHttpParams(pageRequest);
    expect(params.get('page')).toBe('1');
    expect(params.get('size')).toBe('20');
    expect(params.get('sort')).toBe('createdAt,desc');
    expect(params.keys().length).toBe(3);
  });

  it('should ignore undefined properties in PageRequest', () => {
    const pageRequest: PageRequest = { page: undefined, size: 20, sort: undefined };
    const params = toHttpParams(pageRequest);
    expect(params.has('page')).toBe(false);
    expect(params.has('sort')).toBe(false);
    expect(params.get('size')).toBe('20');
  });

  it('should set extra parameters correctly', () => {
    const extra = { search: 'test', filter: 'active' };
    const params = toHttpParams(undefined, extra);
    expect(params.get('search')).toBe('test');
    expect(params.get('filter')).toBe('active');
    expect(params.keys().length).toBe(2);
  });

  it('should ignore undefined values in extra parameters', () => {
    const extra = { search: 'test', filter: undefined };
    const params = toHttpParams(undefined, extra);
    expect(params.get('search')).toBe('test');
    expect(params.has('filter')).toBe(false);
  });

  it('should ignore empty string values in extra parameters', () => {
    const extra = { search: '', filter: 'active' };
    const params = toHttpParams(undefined, extra);
    expect(params.has('search')).toBe(false);
    expect(params.get('filter')).toBe('active');
  });

  it('should set both PageRequest and extra parameters correctly', () => {
    const pageRequest: PageRequest = { page: 1, size: 15 };
    const extra = { search: 'angular', filter: 'completed' };
    const params = toHttpParams(pageRequest, extra);
    expect(params.get('page')).toBe('1');
    expect(params.get('size')).toBe('15');
    expect(params.get('search')).toBe('angular');
    expect(params.get('filter')).toBe('completed');
    expect(params.keys().length).toBe(4);
  });
});
