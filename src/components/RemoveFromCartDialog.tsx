import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

type RemoveFromCartDialogProps = {
  open: boolean;
  productName: string;
  onOpenChange: (open: boolean) => void;
  onMoveToWishlist: () => void;
  onRemoveOnly: () => void;
};

export function RemoveFromCartDialog({
  open,
  productName,
  onOpenChange,
  onMoveToWishlist,
  onRemoveOnly,
}: RemoveFromCartDialogProps) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Remove from cart?</AlertDialogTitle>
          <AlertDialogDescription>
            Move &ldquo;{productName}&rdquo; to your wishlist instead, or remove it from your cart.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel onClick={onRemoveOnly}>Remove only</AlertDialogCancel>
          <AlertDialogAction onClick={onMoveToWishlist}>Move to wishlist</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
