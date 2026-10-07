const CONTROL_CHARS = /[\u0000-\u001F\u007F]/g
const TAGS = /<[^>]*>/g

function plainText(value, maxLength) {
  return value.replace(TAGS, '').replace(CONTROL_CHARS, '').trim().slice(0, maxLength)
}

function isPlace(value) {
  return Boolean(
    value &&
      typeof value.id === 'string' &&
      value.id.trim() &&
      typeof value.name === 'string' &&
      value.name.trim(),
  )
}

export function normalizePlaces(value) {
  if (!Array.isArray(value)) return []
  return value.filter(isPlace).map((place) => ({
    id: plainText(place.id, 128),
    name: plainText(place.name, 120),
    rating: typeof place.rating === 'number' && Number.isFinite(place.rating) ? place.rating : null,
    reviewCount: Number.isInteger(place.reviewCount) && place.reviewCount >= 0 ? place.reviewCount : null,
    reviews: Array.isArray(place.reviews)
      ? place.reviews
          .filter((review) => review && typeof review.content === 'string')
          .map((review) => ({
            source:
              typeof review.source === 'string' && review.source.trim()
                ? plainText(review.source, 40)
                : 'Review',
            content: plainText(review.content, 500),
          }))
          .filter((review) => review.content)
      : [],
  })).filter((place) => place.id && place.name)
}
