import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import PlaceItem from './components/PlaceItem';
import { getMockPlaces } from './mockData';
import { normalizePlaces } from './places';

vi.mock('./mockData', () => ({
  getMockPlaces: vi.fn(),
}));

const pizza = {
  id: '1',
  name: 'Delicious Pizza Place',
  rating: 4.5,
  reviewCount: 120,
  reviews: [{ source: 'MockAdvisor', content: 'Great pizza and friendly staff!' }],
};

describe('normalizePlaces', () => {
  it('drops unsafe review payloads and markup', () => {
    const places = normalizePlaces([
      {
        id: '9',
        name: '<img src=x onerror=alert(1)>Noodle',
        reviews: [{ source: 'Web', content: { secret: 'token' } }, { source: 'Web', content: '<b>Fresh</b> noodles' }],
      },
      { id: ' ', name: 'Missing id' },
      null,
    ]);
    expect(places).toHaveLength(1);
    expect(places[0].name).toBe('Noodle');
    expect(places[0].name).not.toContain('<');
    expect(places[0].reviews).toEqual([{ source: 'Web', content: 'Fresh noodles' }]);
  });

  it('returns an empty list for a non-array payload', () => {
    expect(normalizePlaces(null)).toEqual([]);
  });
});

describe('App', () => {
  beforeEach(() => {
    getMockPlaces.mockReset();
    vi.stubGlobal('fetch', vi.fn());
  });

  it('shows restaurants after the loading state', async () => {
    let resolvePlaces;
    getMockPlaces.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolvePlaces = resolve;
        }),
    );
    render(<App />);
    expect(screen.getByRole('status')).toHaveTextContent(/loading restaurants/i);
    resolvePlaces([pizza]);
    expect(await screen.findByRole('heading', { name: 'Delicious Pizza Place' })).toBeInTheDocument();
    expect(screen.getByText('Great pizza and friendly staff!')).toBeInTheDocument();
  });

  it('shows an empty state', async () => {
    getMockPlaces.mockResolvedValueOnce([]);
    render(<App />);
    expect(await screen.findByText(/no restaurants to show yet/i)).toBeInTheDocument();
  });

  it('shows a fixed error and can retry without revealing the failure text', async () => {
    getMockPlaces.mockRejectedValueOnce(new Error('token=super-secret'));
    render(<App />);
    expect(await screen.findByRole('alert')).toHaveTextContent(/couldn't load restaurants/i);
    expect(screen.queryByText(/super-secret/)).not.toBeInTheDocument();

    getMockPlaces.mockResolvedValueOnce([pizza]);
    fireEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(await screen.findByRole('heading', { name: 'Delicious Pizza Place' })).toBeInTheDocument();
  });

  it('does not crash when reviews are missing and does not request location or the network', async () => {
    const geo = vi.fn();
    Object.defineProperty(navigator, 'geolocation', {
      configurable: true,
      value: { getCurrentPosition: geo, watchPosition: geo },
    });
    getMockPlaces.mockResolvedValueOnce([{ id: '9', name: 'Nameless Fields' }]);
    render(<App />);
    expect(await screen.findByRole('heading', { name: 'Nameless Fields' })).toBeInTheDocument();
    expect(screen.getByText(/no reviews yet/i)).toBeInTheDocument();
    expect(screen.getByText(/does not request your location/i)).toBeInTheDocument();
    expect(geo).not.toHaveBeenCalled();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('renders a hostile name as text', () => {
    const name = '<img src=x onerror=alert(1)>';
    const { container } = render(
      <ul>
        <PlaceItem place={{ id: '1', name, reviews: [] }} />
      </ul>,
    );
    expect(screen.getByRole('heading', { level: 2, name })).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });
});
