//
//  ImportantNumbersDetailViewController
//  Mobile
//
//  Created by Jason Hocker on 9/8/12.
//  Copyright (c) 2012 Ellucian. All rights reserved.
//

#import "ImportantNumbersDetailViewController.h"
#import "MapPOIType.h"
#import "UIViewController+GoogleAnalyticsTrackerSupport.h"
#import "AppearanceChanger.h"
#import "Ellucian_GO-Swift.h"

@implementation ImportantNumbersDetailViewController

- (void)viewDidLoad
{
    [super viewDidLoad];
    
    self.navigationController.navigationBar.translucent = NO;
    
    self.backgroundView.backgroundColor = [UIColor accentColor];
    
    if([AppearanceChanger isIOS8AndRTL]) {
        self.nameLabel.textAlignment = NSTextAlignmentRight;
        self.typeLabel.textAlignment = NSTextAlignmentRight;
        self.phoneLabelLabel.textAlignment = NSTextAlignmentRight;
        self.emailLabelLabel.textAlignment = NSTextAlignmentRight;
        self.addressLabelLabel.textAlignment = NSTextAlignmentRight;
        self.getDirectionsLabel.textAlignment = NSTextAlignmentRight;
        self.addressLabel.textAlignment = NSTextAlignmentRight;
    }
    
    self.separatorAfterPhoneView.backgroundColor = [UIColor accentColor];
    self.separatorAfterEmailView.backgroundColor = [UIColor accentColor];
    self.separatorAfterAddressView.backgroundColor = [UIColor accentColor];
    
    
    self.nameLabel.textColor = [UIColor subheaderTextColor];
    self.typeLabel.textColor = [UIColor subheaderTextColor];
    
    self.nameLabel.text = self.name;

    if([self.types count] > 0) {
        self.typeLabel.text = [self.types componentsJoinedByString:@", "];
    } else {
        self.typeLabel.text = nil;
    }
    self.addressLabel.text = nil;
    
    // Setting the address to nil if the address is set to " ". Mobile server
    // contains a bug during save of importantant numbers where the address field can
    // sometimes inject a space if it's supposed to be nil.
    if (self.address) {
        self.address = [self.address stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
        if([self.address length] == 0) {
            self.address = nil;
        }
    }
    
    if(self.buildingId) {
        NSFetchRequest *request = [NSFetchRequest fetchRequestWithEntityName:@"MapPOI"];
        request.predicate = [NSPredicate predicateWithFormat:@"buildingId = %@", self.buildingId];
        
        NSError *error = nil;
        NSArray *results = [self.module.managedObjectContext executeFetchRequest:request error:&error];
        MapPOI *building = [results lastObject];
        if(building) {
            if(!self.address) {
                self.address = [NSString stringWithFormat:NSLocalizedStringWithDefaultValue(@"building name/address", @"Localizable", [NSBundle mainBundle], @"%@/%@", @"building name/address"), building.name, building.address];
            }

            if([building.latitude doubleValue] != 0 && [building.longitude doubleValue] != 0) {
                self.location = [[CLLocation alloc] initWithLatitude:[building.latitude doubleValue] longitude:[building.longitude doubleValue]];
            }
            if(!self.name) {
                self.name = building.name;
            }

            if(!([self.types count] > 0)) {
                NSMutableArray *types = [[NSMutableArray alloc] init];
                for(MapPOIType *type in building.types) {
                    [types addObject:type.name];
                }
                self.types = [types copy];
            }
        } else {
            [self fetchBuilding];
        }
    }
    
    NSLayoutConstraint *lastSeparator = nil;
    
    if(self.phone || self.phoneExtension) {
        if(self.phone && self.phoneExtension) {
            self.phoneLabel.text = [NSString stringWithFormat:NSLocalizedString(@"%@ ext. %@", @"phone number with phone extension"),self.phone, self.phoneExtension];
        } else if(self.phoneExtension) {
            self.phoneLabel.text = [NSString stringWithFormat:NSLocalizedString(@"ext. %@", @"phone extension"), self.phoneExtension];
        } else {
            self.phoneLabel.text = self.phone;
        }

        [self.phoneView setAction:@selector(tapPhone:) withTarget:self];
        lastSeparator = self.separatorAfterPhoneHeightConstraint;
    } else {
        [[self.phoneView subviews] makeObjectsPerformSelector:@selector(removeFromSuperview)];
        self.separatorAfterPhoneHeightConstraint.constant = 0;
        [self.phoneView addConstraints:
         [NSLayoutConstraint constraintsWithVisualFormat:@"V:[view(0)]"
                                                 options:0 metrics:nil
                                                   views:@{@"view":self.phoneView}]];
    }
    if(self.email) {
        self.emailLabel.text = self.email;
        [self.emailView setAction:@selector(tapEmail:) withTarget:self];
        lastSeparator = self.separatorAfterEmailHeightConstraint;
    } else {
        [[self.emailView subviews] makeObjectsPerformSelector:@selector(removeFromSuperview)];
        self.separatorAfterEmailHeightConstraint.constant = 0;
        [self.emailView addConstraints:
         [NSLayoutConstraint constraintsWithVisualFormat:@"V:[view(0)]"
                                                 options:0 metrics:nil
                                                   views:@{@"view":self.emailView}]];
    }

    if(self.address) {
        self.addressLabel.text = self.address;
        lastSeparator = self.separatorAfterAddressHeightConstraint;
    } else {
        [[self.addressView subviews] makeObjectsPerformSelector:@selector(removeFromSuperview)];
        self.separatorAfterAddressHeightConstraint.constant = 0;
        [self.addressView addConstraints:
         [NSLayoutConstraint constraintsWithVisualFormat:@"V:[view(0)]"
                                                 options:0 metrics:nil
                                                   views:@{@"view":self.addressView}]];
    }
    
    if(self.address || !(self.location.coordinate.latitude == 0 && self.location.coordinate.longitude == 0)) {
        [self.directionsView setAction:@selector(tapDirections:) withTarget:self];
    } else {
        [[self.directionsView subviews] makeObjectsPerformSelector:@selector(removeFromSuperview)];
         lastSeparator.constant = 0;
        [self.directionsView addConstraints:
         [NSLayoutConstraint constraintsWithVisualFormat:@"V:[view(0)]"
                                                 options:0 metrics:nil
                                                   views:@{@"view":self.directionsView}]];
    }
}

-(void)viewDidAppear:(BOOL)animated
{
    [super viewDidAppear:animated];
    [self sendView:@"Important Number Detail" forModuleNamed:self.module.name];
    
    self.widthConstraint.constant = [AppearanceChanger currentScreenBoundsDependOnOrientation].width;
}

-(void)showGetDirections
{
    [self sendEventToTracker1WithCategory:kAnalyticsCategoryUI_Action withAction:kAnalyticsActionInvoke_Native withLabel:@"Get Directions" withValue:nil forModuleNamed:self.module.name];

        CLLocationCoordinate2D coordinate = self.location.coordinate;
        if(coordinate.latitude == 0 && coordinate.longitude == 0) {
            // perform geocode
            CLGeocoder *geocoder = [[CLGeocoder alloc] init];
            
            [geocoder geocodeAddressString:self.address completionHandler:^(NSArray *placemarks, NSError *error) {
                dispatch_async(dispatch_get_main_queue(),^ {
                    if (placemarks.count == 0) {
                        UIAlertView *alert = [[UIAlertView alloc] init];
                        alert.title = NSLocalizedString(@"Unknown address", @"error message when address cannot be used for getting directions");
                        [alert addButtonWithTitle:NSLocalizedString(@"OK", @"OK")];
                        [alert show];
                    } else {
                        CLPlacemark* placemark = [placemarks objectAtIndex:0];
                        [self openPointOnAppleMaps:placemark.location.coordinate];
                    }
                });
            }];
        } else {
            [self openPointOnAppleMaps:coordinate];
        }

}

-(void) openPointOnAppleMaps:(CLLocationCoordinate2D) coordinate
{
    MKPlacemark *place = [[MKPlacemark alloc] initWithCoordinate:coordinate addressDictionary:nil];
    MKMapItem *destinationLocItem = [[MKMapItem alloc] initWithPlacemark:place];
    destinationLocItem.name = self.name;
    NSArray *mapItemsArray = [NSArray arrayWithObject:destinationLocItem];
    NSDictionary *dictForDirections = [NSDictionary dictionaryWithObject:MKLaunchOptionsDirectionsModeDriving forKey:MKLaunchOptionsDirectionsModeKey];
    [MKMapItem openMapsWithItems:mapItemsArray launchOptions:dictForDirections];
}

-(void)tapPhone:(id)sender
{
    [self sendEventToTracker1WithCategory:kAnalyticsCategoryUI_Action withAction:kAnalyticsActionInvoke_Native withLabel:@"Call Phone Number" withValue:nil forModuleNamed:self.module.name];
    NSString *phone = [[self.phone componentsSeparatedByCharactersInSet: [NSCharacterSet characterSetWithCharactersInString:@"() -"]] componentsJoinedByString: @""];
    if(self.phoneExtension) {
        [[UIApplication sharedApplication] openURL:[NSURL URLWithString:[NSString stringWithFormat:@"tel://%@;%@",phone, self.phoneExtension]]];
    } else {
        [[UIApplication sharedApplication] openURL:[NSURL URLWithString:[NSString stringWithFormat:@"tel://%@",phone]]];
    }
}

-(void) tapEmail:(id)sender
{
    [[UIApplication sharedApplication] openURL:[NSURL URLWithString:[NSString stringWithFormat:@"mailto://%@",self.email]]];
}

-(void) tapDirections:(id)sender
{
    [self showGetDirections];
}

-(void) fetchBuilding
{
    NSUserDefaults *defaults = [AppGroupUtilities userDefaults];
    NSString *defaultUrlString = [defaults objectForKey:@"urls-map-buildings"];

    NSString *urlString = [NSString stringWithFormat:@"%@/%@", defaultUrlString, [self.buildingId  stringByAddingPercentEscapesUsingEncoding: NSUTF8StringEncoding]];

    NSError *error;
    [UIApplication sharedApplication].networkActivityIndicatorVisible = YES;
    NSURL *url = [NSURL URLWithString: urlString];
    NSData *responseData = [NSData dataWithContentsOfURL: url];
    
    [UIApplication sharedApplication].networkActivityIndicatorVisible = NO;
    
    if(responseData)
    {
        
        NSDictionary* json = [NSJSONSerialization
                              JSONObjectWithData:responseData
                              options:kNilOptions
                              error:&error];
        
        for ( NSDictionary *building in [json objectForKey:@"buildings"]) {
            if(!self.address && [building objectForKey:@"address"] != [NSNull null]) {
                NSString *address = [[building objectForKey:@"address"] stringByReplacingOccurrencesOfString:@"\\n" withString:@"\n"];
                if( [building objectForKey:@"name"] != [NSNull null]) {
                    address = [NSString stringWithFormat:@"%@\n%@", [building objectForKey:@"name"], address];
                }
                self.address = address;
            }

            if([building objectForKey:@"latitude"] != [NSNull null] && [building objectForKey:@"longitude"] != [NSNull null]) {
                self.location = [[CLLocation alloc] initWithLatitude:[[building objectForKey:@"latitude"] doubleValue] longitude:[[building objectForKey:@"longitude"] doubleValue]];
            }
            if(!self.name && [building objectForKey:@"name"] != [NSNull null])
                self.name = [building objectForKey:@"name"];
            if(!([self.types count] > 0) && [building objectForKey:@"types"] != [NSNull null])
                self.types = [building objectForKey:@"types"];
            
            self.nameLabel.text = self.name;
            if([self.types count] > 0) {
                self.typeLabel.text = [self.types componentsJoinedByString:@", "];
            } else {
                self.typeLabel.text = nil;
            }
            
        }
        
        if(!self.name && !self.description && !self.location && !self.address ) {
            self.nameLabel.text = NSLocalizedString(@"No information available for this building.", @"message when there is no information to show the user about a building they selected");
        }
    }
}

-(void) didRotateFromInterfaceOrientation:(UIInterfaceOrientation)fromInterfaceOrientation
{
    self.widthConstraint.constant = [AppearanceChanger currentScreenBoundsDependOnOrientation].width;
    [self.scrollView setContentOffset:CGPointZero animated:YES];
}

@end
