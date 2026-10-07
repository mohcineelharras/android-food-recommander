import React from 'react';
import PlaceItem from './PlaceItem';

function PlaceList({ places }) {
  const items = Array.isArray(places) ? places : [];
  return (
    <ul className="place-list" aria-label="Restaurants">
      {items.map((place) => (
        <PlaceItem key={place.id} place={place} />
      ))}
    </ul>
  );
}

export default PlaceList;
