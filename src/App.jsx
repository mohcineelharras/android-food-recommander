import React, { useEffect, useState } from 'react';
import { getMockPlaces } from './mockData';
import { normalizePlaces } from './places';
import PlaceList from './components/PlaceList';

const LOAD_ERROR = "Couldn't load restaurants. Please try again.";

function App() {
  const [status, setStatus] = useState('loading');
  const [places, setPlaces] = useState([]);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setStatus('loading');
    getMockPlaces()
      .then((result) => {
        if (cancelled) return;
        setPlaces(normalizePlaces(result));
        setStatus('ready');
      })
      .catch(() => {
        if (cancelled) return;
        setPlaces([]);
        setStatus('error');
      });
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  return (
    <main className="container">
      <h1>Food Recommender</h1>
      <p className="privacy">
        This demo uses built-in sample restaurants. It does not request your location, account, or
        contact details, and it does not send data to a server.
      </p>
      {status === 'loading' && <p role="status">Loading restaurants…</p>}
      {status === 'error' && (
        <div role="alert">
          <p>{LOAD_ERROR}</p>
          <button type="button" onClick={() => setAttempt((value) => value + 1)}>
            Try again
          </button>
        </div>
      )}
      {status === 'ready' && places.length === 0 && <p role="status">No restaurants to show yet.</p>}
      {status === 'ready' && places.length > 0 && <PlaceList places={places} />}
    </main>
  );
}

export default App;
