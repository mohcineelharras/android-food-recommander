import React from 'react';
import PlaceItem from './PlaceItem';

function PlaceList({ places }) {
  return (
    <ul className="place-list">
      {places.map((place) => (
        <PlaceItem key={place.id} place={place} />
      ))}
    </ul>
  );
}

export default PlaceList;
