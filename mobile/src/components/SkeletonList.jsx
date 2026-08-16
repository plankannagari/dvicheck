import { View } from 'react-native';

import SkeletonCard from './SkeletonCard';

export default function SkeletonList({ count = 3, cardHeight = 72, gap = 12 }) {
  return (
    <View style={{ gap }}>
      {Array.from({ length: count }).map((_, i) => (
        <SkeletonCard key={i} height={cardHeight} />
      ))}
    </View>
  );
}
