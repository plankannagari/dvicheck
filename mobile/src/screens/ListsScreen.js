import { useEffect, useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  SafeAreaView, StatusBar, ScrollView, RefreshControl,
  KeyboardAvoidingView, Platform,
} from 'react-native';

import { COLORS } from '../constants';
import useShoppingStore from '../store/shoppingStore';
import useToastStore from '../store/toastStore';
import Toast from '../components/Toast';

const STATUS_META = {
  DRAFT: { label: 'Draft', bg: COLORS.amberLight, text: COLORS.amber },
  ACTIVE: { label: 'Active', bg: COLORS.blueLight, text: COLORS.blue },
  COMPLETED: { label: 'Completed', bg: COLORS.greenLight, text: COLORS.green },
};

export default function ListsScreen() {
  const {
    lists, activeList, items, isLoading, error,
    loadLists, createList, selectList, addItem, toggleItem, removeItem, clearActive,
  } = useShoppingStore();
  const {
    visible: toastVisible, message: toastMessage, type: toastType, showToast, hideToast,
  } = useToastStore();

  const [isCreating, setIsCreating] = useState(false);
  const [newListName, setNewListName] = useState('');
  const [itemName, setItemName] = useState('');
  const [itemQuantity, setItemQuantity] = useState('');
  const [swipedItemId, setSwipedItemId] = useState(null);

  useEffect(() => { loadLists(); }, []);

  const handleCreateList = async () => {
    const name = newListName.trim();
    if (!name) return;
    try {
      await createList(name);
      setNewListName('');
      setIsCreating(false);
    } catch (err) {
      showToast('Could not create list.', 'error');
    }
  };

  const handleNewListBlur = () => {
    // Delay so a tap on the Create button still registers before this hides the row.
    setTimeout(() => {
      setIsCreating(false);
      setNewListName('');
    }, 150);
  };

  const handleAddItem = async () => {
    const name = itemName.trim();
    if (!name) return;
    try {
      const item = await addItem(name, itemQuantity.trim() || undefined);
      setItemName('');
      setItemQuantity('');
      if (item.isDuplicate) {
        showToast(item.duplicateWarning || 'You may already have this item.', 'error');
      }
    } catch (err) {
      showToast('Could not add item.', 'error');
    }
  };

  const handleToggle = async (itemId) => {
    try {
      await toggleItem(itemId);
    } catch (err) {
      showToast('Could not update item.', 'error');
    }
  };

  const handleDelete = async (itemId) => {
    try {
      await removeItem(itemId);
    } catch (err) {
      showToast('Could not remove item.', 'error');
    } finally {
      setSwipedItemId(null);
    }
  };

  if (activeList) {
    const duplicateCount = items.filter((item) => item.isDuplicate).length;

    return (
      <SafeAreaView style={styles.safeArea}>
        <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />

        <View style={styles.detailHeader}>
          <TouchableOpacity onPress={clearActive} style={styles.backBtn} activeOpacity={0.7}>
            <Text style={styles.backArrow}>←</Text>
          </TouchableOpacity>
          <Text style={styles.detailTitle} numberOfLines={1}>{activeList.name}</Text>
          <View style={styles.backBtn} />
        </View>

        {duplicateCount > 0 && (
          <View style={styles.duplicateBanner}>
            <Text style={styles.duplicateBannerText}>
              ⚠️ {duplicateCount} item{duplicateCount > 1 ? 's' : ''} may already be in your pantry — check your pantry first
            </Text>
          </View>
        )}

        <KeyboardAvoidingView
          style={styles.flex1}
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
          keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 0}
        >
          <ScrollView
            style={styles.scroll}
            contentContainerStyle={styles.itemsScrollContent}
            refreshControl={
              <RefreshControl
                refreshing={isLoading}
                onRefresh={() => selectList(activeList.id)}
                tintColor={COLORS.accent}
              />
            }
          >
            {items.length === 0 ? (
              <View style={styles.emptyState}>
                <Text style={styles.emptyIcon}>🛒</Text>
                <Text style={styles.emptyText}>No items yet</Text>
                <Text style={styles.emptySub}>Add your first item below</Text>
              </View>
            ) : (
              items.map((item) => (
                <TouchableOpacity
                  key={item.id}
                  style={styles.itemRow}
                  activeOpacity={0.9}
                  onLongPress={() => setSwipedItemId(swipedItemId === item.id ? null : item.id)}
                >
                  <TouchableOpacity
                    style={[styles.checkbox, item.isChecked && styles.checkboxChecked]}
                    onPress={() => handleToggle(item.id)}
                    activeOpacity={0.7}
                  >
                    {item.isChecked && <Text style={styles.checkmark}>✓</Text>}
                  </TouchableOpacity>

                  <View style={styles.itemMeta}>
                    <Text
                      style={[styles.itemName, item.isChecked && styles.itemTextChecked]}
                      numberOfLines={1}
                    >
                      {item.name}
                    </Text>
                    {item.isDuplicate && (
                      <Text style={styles.itemWarning} numberOfLines={2}>{item.duplicateWarning}</Text>
                    )}
                  </View>

                  <Text style={[styles.itemQuantity, item.isChecked && styles.itemTextChecked]}>
                    {item.quantity}
                  </Text>

                  {swipedItemId === item.id && (
                    <TouchableOpacity
                      style={styles.deleteBtn}
                      onPress={() => handleDelete(item.id)}
                      activeOpacity={0.8}
                    >
                      <Text style={styles.deleteBtnText}>Delete</Text>
                    </TouchableOpacity>
                  )}
                </TouchableOpacity>
              ))
            )}
          </ScrollView>

          <View style={styles.addItemBar}>
            <TextInput
              style={styles.addItemInput}
              placeholder="Add an item"
              placeholderTextColor={COLORS.inkFaint}
              value={itemName}
              onChangeText={setItemName}
              onSubmitEditing={handleAddItem}
              returnKeyType="done"
            />
            <TextInput
              style={styles.addQtyInput}
              placeholder="qty"
              placeholderTextColor={COLORS.inkFaint}
              value={itemQuantity}
              onChangeText={setItemQuantity}
              onSubmitEditing={handleAddItem}
              returnKeyType="done"
            />
            <TouchableOpacity style={styles.addBtn} onPress={handleAddItem} activeOpacity={0.8}>
              <Text style={styles.addBtnText}>Add</Text>
            </TouchableOpacity>
          </View>
        </KeyboardAvoidingView>

        <Toast visible={toastVisible} message={toastMessage} type={toastType} onHide={hideToast} />
      </SafeAreaView>
    );
  }

  // index mode
  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />

      <View style={styles.header}>
        <Text style={styles.heading}>Shopping Lists</Text>
        <TouchableOpacity onPress={() => setIsCreating(true)} activeOpacity={0.8}>
          <Text style={styles.newListBtn}>+ New List</Text>
        </TouchableOpacity>
      </View>

      {isCreating && (
        <View style={styles.newListRow}>
          <TextInput
            style={styles.newListInput}
            placeholder="List name"
            placeholderTextColor={COLORS.inkFaint}
            value={newListName}
            onChangeText={setNewListName}
            onBlur={handleNewListBlur}
            onSubmitEditing={handleCreateList}
            autoFocus
            returnKeyType="done"
          />
          <TouchableOpacity style={styles.createBtn} onPress={handleCreateList} activeOpacity={0.8}>
            <Text style={styles.createBtnText}>Create</Text>
          </TouchableOpacity>
        </View>
      )}

      {error ? (
        <View style={styles.errorBanner}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      ) : null}

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={isLoading} onRefresh={loadLists} tintColor={COLORS.accent} />
        }
      >
        {lists.length === 0 && !isLoading ? (
          <View style={styles.emptyState}>
            <Text style={styles.emptyIcon}>🛒</Text>
            <Text style={styles.emptyText}>No lists yet. Tap + to create one.</Text>
          </View>
        ) : (
          lists.map((list) => {
            const meta = STATUS_META[list.status] ?? { label: list.status, bg: COLORS.border, text: COLORS.inkLight };
            return (
              <TouchableOpacity
                key={list.id}
                style={styles.listCard}
                onPress={() => selectList(list.id)}
                activeOpacity={0.8}
              >
                <View style={styles.listCardMeta}>
                  <Text style={styles.listName} numberOfLines={1}>{list.name}</Text>
                  <Text style={styles.listItemCount}>
                    {list.itemCount} item{list.itemCount === 1 ? '' : 's'}
                  </Text>
                </View>
                <View style={[styles.statusBadge, { backgroundColor: meta.bg }]}>
                  <Text style={[styles.statusBadgeText, { color: meta.text }]}>{meta.label}</Text>
                </View>
              </TouchableOpacity>
            );
          })
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: COLORS.bg },
  flex1: { flex: 1 },
  scroll: { flex: 1 },
  scrollContent: { padding: 16, paddingBottom: 20 },

  header: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    padding: 16, paddingHorizontal: 24,
  },
  heading: { fontSize: 22, color: COLORS.ink, fontWeight: '600' },
  newListBtn: { fontSize: 14, color: COLORS.accent, fontWeight: '600' },

  newListRow: {
    flexDirection: 'row', alignItems: 'center', gap: 10,
    marginHorizontal: 16, marginBottom: 12,
  },
  newListInput: {
    flex: 1, backgroundColor: COLORS.card, borderRadius: 12,
    borderWidth: 1, borderColor: COLORS.border,
    paddingHorizontal: 14, paddingVertical: 10, fontSize: 14, color: COLORS.ink,
  },
  createBtn: {
    backgroundColor: COLORS.accent, borderRadius: 12,
    paddingVertical: 11, paddingHorizontal: 18,
  },
  createBtnText: { color: '#fff', fontSize: 14, fontWeight: '600' },

  errorBanner: {
    backgroundColor: COLORS.redLight, padding: 10, marginHorizontal: 16,
    borderRadius: 10, marginBottom: 12,
  },
  errorText: { fontSize: 12, color: COLORS.red },

  listCard: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    backgroundColor: COLORS.card, borderRadius: 16, padding: 16,
    borderWidth: 1, borderColor: COLORS.border, marginBottom: 12,
  },
  listCardMeta: { flex: 1, marginRight: 12 },
  listName: { fontSize: 16, color: COLORS.ink, fontWeight: '600', marginBottom: 4 },
  listItemCount: { fontSize: 12, color: COLORS.inkLight },
  statusBadge: { borderRadius: 12, paddingHorizontal: 10, paddingVertical: 5 },
  statusBadgeText: { fontSize: 11, fontWeight: '600' },

  emptyState: { padding: 40, alignItems: 'center' },
  emptyIcon: { fontSize: 40, marginBottom: 12 },
  emptyText: { fontSize: 14, color: COLORS.inkLight, textAlign: 'center' },
  emptySub: { fontSize: 13, color: COLORS.inkLight, textAlign: 'center', marginTop: 4 },

  // detail mode
  detailHeader: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingVertical: 12,
  },
  backBtn: { width: 32, height: 32, alignItems: 'flex-start', justifyContent: 'center' },
  backArrow: { fontSize: 22, color: COLORS.ink },
  detailTitle: { flex: 1, textAlign: 'center', fontSize: 17, color: COLORS.ink, fontWeight: '600' },

  duplicateBanner: {
    backgroundColor: COLORS.amberLight, marginHorizontal: 16, marginBottom: 12,
    borderRadius: 12, padding: 12,
  },
  duplicateBannerText: { fontSize: 12, color: COLORS.amber, lineHeight: 18 },

  itemsScrollContent: { padding: 16, paddingBottom: 12 },
  itemRow: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: COLORS.card, borderRadius: 14, padding: 14,
    borderWidth: 1, borderColor: COLORS.border, marginBottom: 10, gap: 12,
  },
  checkbox: {
    width: 24, height: 24, borderRadius: 12,
    borderWidth: 2, borderColor: COLORS.border,
    alignItems: 'center', justifyContent: 'center',
  },
  checkboxChecked: { backgroundColor: COLORS.green, borderColor: COLORS.green },
  checkmark: { color: '#fff', fontSize: 13, fontWeight: '700' },
  itemMeta: { flex: 1 },
  itemName: { fontSize: 14, color: COLORS.ink },
  itemTextChecked: { color: COLORS.inkFaint, textDecorationLine: 'line-through' },
  itemWarning: { fontSize: 11, color: COLORS.amber, marginTop: 3 },
  itemQuantity: { fontSize: 13, color: COLORS.inkLight },
  deleteBtn: {
    backgroundColor: COLORS.red, borderRadius: 10,
    paddingHorizontal: 12, paddingVertical: 8,
  },
  deleteBtnText: { color: '#fff', fontSize: 12, fontWeight: '600' },

  addItemBar: {
    flexDirection: 'row', alignItems: 'center', gap: 8,
    padding: 16, backgroundColor: COLORS.card,
    borderTopWidth: 1, borderTopColor: COLORS.border,
  },
  addItemInput: {
    flex: 1, backgroundColor: COLORS.bg, borderRadius: 12,
    borderWidth: 1, borderColor: COLORS.border,
    paddingHorizontal: 14, paddingVertical: 10, fontSize: 14, color: COLORS.ink,
  },
  addQtyInput: {
    width: 56, backgroundColor: COLORS.bg, borderRadius: 12,
    borderWidth: 1, borderColor: COLORS.border,
    paddingHorizontal: 10, paddingVertical: 10, fontSize: 14, color: COLORS.ink,
    textAlign: 'center',
  },
  addBtn: {
    backgroundColor: COLORS.accent, borderRadius: 12,
    paddingVertical: 11, paddingHorizontal: 16,
  },
  addBtnText: { color: '#fff', fontSize: 14, fontWeight: '600' },
});
