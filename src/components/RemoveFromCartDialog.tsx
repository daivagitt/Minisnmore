import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";

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
            Save &ldquo;{productName}&rdquo; to your wishlist instead, or remove it from cart.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <Button type="button" variant="outline" onClick={onRemoveOnly}>
            Remove only
          </Button>
          <AlertDialogAction onClick={onMoveToWishlist}>Save to wishlist</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
