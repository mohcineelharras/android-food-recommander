// src/mockData.js
export const getMockPlaces = async () => {
  // Simulate network delay
  await new Promise((resolve) => setTimeout(resolve, 500));

  return [
    {
      id: '1',
      name: 'Delicious Pizza Place',
      rating: 4.5,
      reviewCount: 120,
      reviews: [
        { source: 'MockAdvisor', content: 'Great pizza and friendly staff!' },
        { source: 'MockTwitter', content: 'Best pizza in town, hands down.' },
      ],
    },
    {
      id: '2',
      name: 'Tasty Burger Joint',
      rating: 4.0,
      reviewCount: 85,
      reviews: [
        { source: 'MockAdvisor', content: 'Juicy burgers and crispy fries.' },
        { source: 'MockTwitter', content: 'The burgers here are amazing!' },
      ],
    },
    {
      id: '3',
      name: 'Awesome Sushi Restaurant',
      rating: 4.8,
      reviewCount: 200,
      reviews: [
          {source: 'MockAdvisor', content: 'Very fresh and delicious sushi'},
          {source: 'MockTwitter', content: 'I love this place. Highly recommended!'}
      ]
    }
  ];
};
