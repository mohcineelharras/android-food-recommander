import React from 'react';

function PlaceItem({ place }) {
  const name = typeof place?.name === 'string' && place.name.trim() ? place.name.trim() : 'Unnamed restaurant';
  const reviews = Array.isArray(place?.reviews) ? place.reviews : [];
  const visibleReviews = reviews.filter((review) => typeof review?.content === 'string' && review.content);

  return (
    <li className="place-item">
      <h2>{name}</h2>
      {typeof place?.rating === 'number' && Number.isFinite(place.rating) && <p>Rating: {place.rating}</p>}
      {Number.isInteger(place?.reviewCount) && place.reviewCount >= 0 && (
        <p>Review count: {place.reviewCount}</p>
      )}
      <div className="reviews">
        {visibleReviews.length === 0 && <p>No reviews yet</p>}
        {visibleReviews.map((review, index) => (
          <div key={`${review.source ?? 'review'}-${index}`} className="review">
            <p>Source: {typeof review.source === 'string' ? review.source : 'Review'}</p>
            <p>{review.content}</p>
          </div>
        ))}
      </div>
    </li>
  );
}

export default PlaceItem;
