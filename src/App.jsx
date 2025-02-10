import React, { useState, useEffect } from 'react';
import { getMockPlaces } from './mockData';
import PlaceList from './components/PlaceList';

function App() {
  const [places, setPlaces] = useState([]);

  useEffect(() => {
    // Simulate fetching data
    const fetchData = async () => {
      const mockPlaces = await getMockPlaces();
      setPlaces(mockPlaces);
    };

    fetchData();
  }, []);

  return (
    <div className="container">
      <h1>Food Recommender</h1>
      <PlaceList places={places} />
    </div>
  );
}

export default App;
