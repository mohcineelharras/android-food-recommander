import React from 'react';

function PlaceItem({ place }) {
  return (
    <li className="place-item">
      <h2>{place.name}</h2>
      <p>Rating: {place.rating}</p>
      <p>Review Count: {place.reviewCount}</p>
      <div className="reviews">
        {place.reviews.map((review, index) => (
            <div key={index} className="review">
                <p>Source: {review.source}</p>
                <p>{review.content}</p>
            </div>
        ))}
      </div>
    </li>
  );
}

export default PlaceItem;
